package com.quizmaster.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityIT {
    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18");
    private static final String JWT_SECRET = randomSecret();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("quizmaster.jwt-secret", () -> JWT_SECRET);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Autowired TestRestTemplate http;
    @Autowired AccountRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;
    private String password;
    private Account admin;
    private Account student;

    @BeforeEach
    void fixtures() {
        // This connection belongs exclusively to the disposable Testcontainers DB.
        jdbc.execute("TRUNCATE TABLE account");
        password = UUID.randomUUID().toString();
        admin = seed("admin@example.test", Role.ADMIN, true);
        student = seed("student@example.test", Role.STUDENT, true);
    }

    Account seed(String email, Role role, boolean active) {
        var account = new Account(UUID.randomUUID(), email, encoder.encode(password), role, active);
        repository.insert(account, Instant.now());
        return account;
    }

    ResponseEntity<JsonNode> request(HttpMethod method, String path, String token, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    ResponseEntity<JsonNode> login(String email, String candidate) {
        return request(HttpMethod.POST, "/auth/login", null, Map.of("email", email, "password", candidate));
    }

    String token(Account account) {
        var response = login(account.email(), password);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().get("accessToken").asText();
    }

    @Test
    void validLoginNormalizesEmailAndReturnsSafeIdentity() {
        var login = login("  STUDENT@example.test  ", password);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getHeaders().getCacheControl()).contains("no-store");
        assertThat(login.getBody().get("expiresIn").asInt()).isEqualTo(900);
        var me = request(HttpMethod.GET, "/auth/me", login.getBody().get("accessToken").asText(), null);
        assertThat(me.getBody().get("id").asText()).isEqualTo(student.id().toString());
        assertThat(me.getBody().toString()).doesNotContain("password", "hash", password);
    }

    @Test
    void loginFailuresAreIndistinguishable() {
        var unknown = login("unknown@example.test", password);
        var wrong = login(student.email(), UUID.randomUUID().toString());
        var inactive = seed("inactive@example.test", Role.STUDENT, false);
        var disabled = login(inactive.email(), password);
        for (var response : List.of(unknown, wrong, disabled)) {
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getBody()).isEqualTo(unknown.getBody());
        }
    }

    @Test
    void missingAndMalformedBearerAreUnauthorized() {
        for (String bearer : List.of("", "invalid.jwt.value")) {
            var response = request(HttpMethod.GET, "/auth/me", bearer.isEmpty() ? null : bearer, null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "subject", "future", "missingExpiry", "signature"})
    void invalidClaimsOrSignatureAreRejected(String kind) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(kind.equals("issuer") ? "other" : "quizmaster-qa")
                .audience(List.of(kind.equals("audience") ? "other" : "quizmaster-api"))
                .subject(kind.equals("subject") ? "invalid-uuid" : student.id().toString())
                .issuedAt(kind.equals("future") ? now.plusSeconds(60) : now.minusSeconds(60));
        if (!kind.equals("missingExpiry")) claims.expiresAt(kind.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(300));
        String value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        if (kind.equals("signature")) {
            int dot = value.lastIndexOf('.') + 1;
            value = value.substring(0, dot) + (value.charAt(dot) == 'A' ? 'B' : 'A') + value.substring(dot + 1);
        }
        assertThat(request(HttpMethod.GET, "/auth/me", value, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void bothNonAdminRolesAreDeniedBeforeBodyValidation() {
        var instructor = seed("instructor@example.test", Role.INSTRUCTOR, true);
        for (var account : List.of(student, instructor)) {
            var bearer = token(account);
            assertThat(request(HttpMethod.POST, "/users", bearer, Map.of()).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.GET, "/users", bearer, null).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.PATCH, "/users/" + admin.id() + "/status", bearer, Map.of("active", false))
                    .getStatusCode().value()).isEqualTo(403);
        }
        assertThat(repository.byId(admin.id()).orElseThrow().active()).isTrue();
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void forgedRoleClaimDoesNotGrantAdminAccess() {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("quizmaster-qa").audience(List.of("quizmaster-api"))
                .subject(student.id().toString()).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("role", "ADMIN").build();
        var value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThat(request(HttpMethod.GET, "/users", value, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void adminCreatesHashedAccountAndRejectsDuplicateEmail() {
        var bearer = token(admin);
        var body = Map.of("email", " NEW@example.test ", "password", password, "role", "STUDENT", "active", true);
        var created = request(HttpMethod.POST, "/users", bearer, body);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getLocation().toString()).startsWith("/api/v1/users/");
        assertThat(created.getBody().toString()).doesNotContain("password", "hash", password);
        var persisted = repository.byEmail("new@example.test").orElseThrow();
        assertThat(persisted.passwordHash()).isNotEqualTo(password);
        assertThat(encoder.matches(password, persisted.passwordHash())).isTrue();
        assertThat(request(HttpMethod.POST, "/users", bearer, body).getStatusCode().value()).isEqualTo(409);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void concurrentDuplicateCreationCommitsExactlyOneAccount() throws Exception {
        var bearer = token(admin);
        var body = Map.of("email", "same@example.test", "password", password, "role", "STUDENT", "active", true);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return request(HttpMethod.POST, "/users", bearer, body).getStatusCode().value();
            })).toList();
            gate.countDown();
            assertThat(List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void deactivationRejectsAlreadyIssuedToken() {
        var bearer = token(student);
        var status = request(HttpMethod.PATCH, "/users/" + student.id() + "/status", token(admin), Map.of("active", false));
        assertThat(status.getStatusCode().value()).isEqualTo(200);
        assertThat(request(HttpMethod.GET, "/auth/me", bearer, null).getStatusCode().value()).isEqualTo(401);
        assertThat(login(student.email(), password).getStatusCode().value()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"email\":\"bad\",\"password\":\"short\"}",
        "{\"email\":\"student@example.test\"}",
        "{\"email\":\"student@example.test\",\"password\":null}",
        "{\"email\":\"student@example.test\",\"password\":\"placeholder-only\",\"role\":\"ADMIN\"}",
        "{broken"
    })
    void malformedLoginIsRejectedWithoutEchoingSecrets(String body) {
        var response = request(HttpMethod.POST, "/auth/login", null, body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).doesNotContain("placeholder-only", "short", "stackTrace");
    }

    @Test
    void adminInputAndPaginationAreValidated() {
        var bearer = token(admin);
        for (var path : List.of("/users?page=-1", "/users?size=0", "/users?size=101", "/users?page=text")) {
            assertThat(request(HttpMethod.GET, path, bearer, null).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(request(HttpMethod.POST, "/users", bearer, Map.of("email", "valid@example.test", "password", password,
                "role", "OWNER", "active", true)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.PATCH, "/users/not-a-uuid/status", bearer, Map.of("active", true)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.PATCH, "/users/" + UUID.randomUUID() + "/status", bearer, Map.of("active", true)).getStatusCode().value()).isEqualTo(404);
        var page = request(HttpMethod.GET, "/users?size=1", bearer, null).getBody();
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("total").asInt()).isEqualTo(2);
    }

    @Test
    void passwordMaximumSupportsUnicodeWithoutTruncation() {
        String longPassword = "\u00e9".repeat(128);
        var body = Map.of("email", "long@example.test", "password", longPassword, "role", "STUDENT", "active", true);
        assertThat(request(HttpMethod.POST, "/users", token(admin), body).getStatusCode().value()).isEqualTo(201);
        assertThat(login("long@example.test", longPassword).getStatusCode().value()).isEqualTo(200);
        assertThat(login("long@example.test", longPassword.substring(0, 127)).getStatusCode().value()).isEqualTo(401);
        assertThat(login("long@example.test", longPassword + "x").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void numericRolesAndStringBooleansAreRejected() {
        var bearer = token(admin);
        assertThat(request(HttpMethod.POST, "/users", bearer, Map.of("email", "numeric@example.test", "password", password,
                "role", 0, "active", true)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.PATCH, "/users/" + student.id() + "/status", bearer, Map.of("active", "true"))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void databaseRejectsNonNormalizedEmailAndInvalidRole() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO account VALUES (?,?,?,?,?,now())", UUID.randomUUID(), " UPPER@example.test ", "hash", "STUDENT", true))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO account VALUES (?,?,?,?,?,now())", UUID.randomUUID(), "other@example.test", "hash", "OWNER", true))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void taxonomyEnforcesRolesNormalizationParentAndPagination() {
        var adminToken = token(admin);
        var categoryResponse = request(HttpMethod.POST, "/categories", adminToken, Map.of("name", "  QA " + UUID.randomUUID() + "  "));
        assertThat(categoryResponse.getStatusCode().value()).isEqualTo(201);
        assertThat(categoryResponse.getBody().get("name").asText()).startsWith("QA ");
        var categoryId = categoryResponse.getBody().get("id").asText();
        var normalizedName = categoryResponse.getBody().get("name").asText();
        assertThat(request(HttpMethod.POST, "/categories", adminToken, Map.of("name", normalizedName.toUpperCase())).getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.POST, "/categories", adminToken, Map.of("name", " ")).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/categories", adminToken, Map.of("name", "x".repeat(121))).getStatusCode().value()).isEqualTo(400);

        var studentToken = token(student);
        var instructor = seed("taxonomy-instructor@example.test", Role.INSTRUCTOR, true);
        var instructorToken = token(instructor);
        for (String bearer : List.of(studentToken, instructorToken)) {
            assertThat(request(HttpMethod.POST, "/categories", bearer, Map.of("name", "denied")).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.POST, "/subjects", bearer,
                    Map.of("categoryId", categoryId, "name", "denied")).getStatusCode().value()).isEqualTo(403);
        }
        assertThat(request(HttpMethod.GET, "/categories", studentToken, null).getStatusCode().value()).isEqualTo(403);
        for (String bearer : List.of(adminToken, instructorToken)) {
            assertThat(request(HttpMethod.GET, "/categories", bearer, null).getStatusCode().value()).isEqualTo(200);
            assertThat(request(HttpMethod.GET, "/subjects", bearer, null).getStatusCode().value()).isEqualTo(200);
        }

        assertThat(request(HttpMethod.POST, "/subjects", adminToken,
                Map.of("categoryId", UUID.randomUUID(), "name", "unknown-parent")).getStatusCode().value()).isEqualTo(404);
        var subjectBody = Map.of("categoryId", categoryId, "name", "  Testing " + UUID.randomUUID() + "  ");
        var subjectResponse = request(HttpMethod.POST, "/subjects", adminToken, subjectBody);
        assertThat(subjectResponse.getStatusCode().value()).isEqualTo(201);
        assertThat(subjectResponse.getBody().get("name").asText()).startsWith("Testing ");
        assertThat(request(HttpMethod.POST, "/subjects", adminToken,
                Map.of("categoryId", categoryId, "name", subjectResponse.getBody().get("name").asText().toUpperCase())).getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.GET, "/subjects?categoryId=" + UUID.randomUUID(), adminToken, null).getStatusCode().value()).isEqualTo(404);
        for (var path : List.of("/categories?page=-1", "/categories?size=0", "/subjects?size=101", "/subjects?page=text")) {
            assertThat(request(HttpMethod.GET, path, adminToken, null).getStatusCode().value()).isEqualTo(400);
        }
        var filtered = request(HttpMethod.GET, "/subjects?categoryId=" + categoryId + "&size=1", adminToken, null).getBody();
        assertThat(filtered.get("items").size()).isEqualTo(1);
        assertThat(filtered.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentNormalizedCategoryCreationCommitsExactlyOneRow() throws Exception {
        var bearer = token(admin);
        var name = "concurrent " + UUID.randomUUID();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return request(HttpMethod.POST, "/categories", bearer,
                        Map.of("name", i == 0 ? name : name.toUpperCase())).getStatusCode().value();
            })).toList();
            gate.countDown();
            assertThat(List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category WHERE normalized_name = ?", Integer.class, name)).isEqualTo(1);
    }
}
