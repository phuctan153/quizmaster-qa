package com.quizmaster.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.MediaType;
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
        jdbc.execute("TRUNCATE TABLE exam_option_snapshot, exam_question_snapshot, exam_draft_question, exam_version, question_option, question, account");
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

    ResponseEntity<JsonNode> upload(String token, byte[] content) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return "questions.csv"; }
        });
        return http.exchange("/api/v1/questions/import", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
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

    @Test
    void questionBankEnforcesOwnershipAndSingleCorrectOption() {
        var adminToken = token(admin);
        var subjectId = createSubject(adminToken);
        var instructor = seed("question-owner@example.test", Role.INSTRUCTOR, true);
        var instructorToken = token(instructor);
        var created = request(HttpMethod.POST, "/questions", instructorToken, questionBody(subjectId, "  Which option is correct?  "));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var questionId = created.getBody().get("id").asText();
        assertThat(created.getBody().get("ownerId").asText()).isEqualTo(instructor.id().toString());
        assertThat(created.getBody().get("prompt").asText()).isEqualTo("Which option is correct?");
        assertThat(created.getBody().get("revision").asInt()).isZero();
        assertThat(created.getBody().get("options").size()).isEqualTo(2);
        assertThat(java.util.stream.StreamSupport.stream(created.getBody().get("options").spliterator(), false)
                .filter(option -> option.get("correct").asBoolean()).count()).isEqualTo(1);

        var invalidCorrect = questionBody(subjectId, "No correct option");
        invalidCorrect.put("options", List.of(Map.of("label", "A", "text", "First", "correct", false),
                Map.of("label", "B", "text", "Second", "correct", false)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, invalidCorrect).getStatusCode().value()).isEqualTo(400);
        var multipleCorrect = questionBody(subjectId, "Multiple correct options");
        multipleCorrect.put("options", List.of(Map.of("label", "A", "text", "First", "correct", true),
                Map.of("label", "B", "text", "Second", "correct", true)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, multipleCorrect).getStatusCode().value()).isEqualTo(400);
        var oneOption = questionBody(subjectId, "Too few options");
        oneOption.put("options", List.of(Map.of("label", "A", "text", "Only answer", "correct", true)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, oneOption).getStatusCode().value()).isEqualTo(400);
        var tooManyOptions = questionBody(subjectId, "Too many options");
        tooManyOptions.put("options", List.of(
                Map.of("label", "A", "text", "Answer A", "correct", true),
                Map.of("label", "B", "text", "Answer B", "correct", false),
                Map.of("label", "C", "text", "Answer C", "correct", false),
                Map.of("label", "D", "text", "Answer D", "correct", false),
                Map.of("label", "E", "text", "Answer E", "correct", false),
                Map.of("label", "F", "text", "Answer F", "correct", false),
                Map.of("label", "G", "text", "Answer G", "correct", false)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, tooManyOptions).getStatusCode().value()).isEqualTo(400);
        var duplicateLabels = questionBody(subjectId, "Duplicate labels");
        duplicateLabels.put("options", List.of(Map.of("label", "A", "text", "First", "correct", true),
                Map.of("label", "a", "text", "Second", "correct", false)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, duplicateLabels).getStatusCode().value()).isEqualTo(400);
        var duplicateTexts = questionBody(subjectId, "Duplicate option text");
        duplicateTexts.put("options", List.of(Map.of("label", "A", "text", "Same", "correct", true),
                Map.of("label", "B", "text", " same ", "correct", false)));
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, duplicateTexts).getStatusCode().value()).isEqualTo(400);
        var unknownField = questionBody(subjectId, "Unknown field");
        unknownField.put("unexpected", true);
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, unknownField).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/questions", instructorToken, questionBody(UUID.randomUUID(), "Unknown subject"))
                .getStatusCode().value()).isEqualTo(404);

        var otherInstructor = seed("question-other@example.test", Role.INSTRUCTOR, true);
        var otherToken = token(otherInstructor);
        assertThat(request(HttpMethod.GET, "/questions/" + questionId, otherToken, null).getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.PUT, "/questions/" + questionId, otherToken,
                updateBody(subjectId, 0, "Foreign update")).getStatusCode().value()).isEqualTo(404);
        var ownList = request(HttpMethod.GET, "/questions?subjectId=" + subjectId + "&size=1", instructorToken, null).getBody();
        assertThat(ownList.get("items").size()).isEqualTo(1);
        assertThat(ownList.get("total").asInt()).isEqualTo(1);
        assertThat(request(HttpMethod.GET, "/questions", otherToken, null).getBody().get("total").asInt()).isZero();
        var studentToken = token(student);
        for (String deniedToken : List.of(studentToken, adminToken)) {
            assertThat(request(HttpMethod.GET, "/questions/" + questionId, deniedToken, null).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.POST, "/questions", deniedToken, questionBody(subjectId, "Denied create")).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.PUT, "/questions/" + questionId, deniedToken,
                    updateBody(subjectId, 0, "Denied update")).getStatusCode().value()).isEqualTo(403);
        }

        var noDifficulty = questionBody(subjectId, "Difficulty is optional");
        noDifficulty.remove("difficulty");
        var optionalDifficulty = request(HttpMethod.POST, "/questions", instructorToken, noDifficulty);
        assertThat(optionalDifficulty.getStatusCode().value()).isEqualTo(201);
        assertThat(optionalDifficulty.getBody().get("difficulty").isNull()).isTrue();
    }

    @Test
    void questionUpdateUsesRevisionForConcurrentWriters() throws Exception {
        var subjectId = createSubject(token(admin));
        var instructor = seed("question-race@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var created = request(HttpMethod.POST, "/questions", bearer, questionBody(subjectId, "Original prompt"));
        var questionId = created.getBody().get("id").asText();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return request(HttpMethod.PUT, "/questions/" + questionId, bearer,
                        updateBody(subjectId, 0, "Concurrent prompt " + i)).getStatusCode().value();
            })).toList();
            gate.countDown();
            assertThat(List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        var saved = request(HttpMethod.GET, "/questions/" + questionId, bearer, null).getBody();
        assertThat(saved.get("revision").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_option WHERE question_id=?", Integer.class,
                UUID.fromString(questionId))).isEqualTo(2);
    }

    @Test
    void csvQuestionImportIsQuotedAndAtomic() {
        var subjectId = createSubject(token(admin));
        var instructor = seed("csv-import@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var valid = csvRow(subjectId, "\"Choose, one?\nRead carefully\",\"First, answer\",Second,Third,Fourth,B");
        var imported = upload(bearer, valid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(imported.getStatusCode().value()).isEqualTo(201);
        assertThat(imported.getBody().get("imported").asInt()).isEqualTo(1);
        var id = imported.getBody().get("questionIds").get(0).asText();
        var question = request(HttpMethod.GET, "/questions/" + id, bearer, null).getBody();
        assertThat(question.get("prompt").asText()).isEqualTo("Choose, one?\nRead carefully");
        assertThat(question.get("options").get(0).get("text").asText()).isEqualTo("First, answer");
        assertThat(question.get("options").get(1).get("correct").asBoolean()).isTrue();

        var exactLimit = valid + "\n";
        exactLimit += " ".repeat(1024 * 1024 - exactLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(exactLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(1024 * 1024);
        var atLimit = upload(bearer, exactLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(atLimit.getStatusCode().value()).isEqualTo(201);
        assertThat(atLimit.getBody().get("imported").asInt()).isEqualTo(1);

        int before = jdbc.queryForObject("SELECT count(*) FROM question WHERE owner_id=?", Integer.class, instructor.id());
        var invalidBatch = csvRow(subjectId, "valid row,one,two,three,four,A") + "\n" + subjectId + ",bad,one,two,three,four,Z";
        var rejected = upload(bearer, invalidBatch.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(rejected.getBody().get("fieldErrors").toString()).contains("rows[3].correctLabel");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question WHERE owner_id=?", Integer.class, instructor.id())).isEqualTo(before);
        assertThat(upload(bearer, ("wrong,header\n" + subjectId + ",x,a,b,c,d,A").getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(upload(bearer, new byte[] {(byte) 0xc3, (byte) 0x28}).getStatusCode().value()).isEqualTo(400);
        assertThat(upload(bearer, new byte[1024 * 1024 + 1]).getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void csvImportAcceptsFiveHundredRowsAndRejectsFiveHundredOne() {
        var subjectId = createSubject(token(admin));
        var instructor = seed("csv-limit@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var header = "subjectId,prompt,optionA,optionB,optionC,optionD,correctLabel\n";
        var row = subjectId + ",prompt,one,two,three,four,A\n";
        var fiveHundred = header + row.repeat(500);
        var accepted = upload(bearer, fiveHundred.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(accepted.getStatusCode().value()).isEqualTo(201);
        assertThat(accepted.getBody().get("imported").asInt()).isEqualTo(500);
        var fiveHundredOne = header + row.repeat(501);
        assertThat(upload(bearer, fiveHundredOne.getBytes(java.nio.charset.StandardCharsets.UTF_8)).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question WHERE owner_id=?", Integer.class, instructor.id())).isEqualTo(500);
    }

    @Test
    void examDraftPublishesOwnedImmutableQuestionSnapshot() {
        var adminToken = token(admin);
        var subjectId = createSubject(adminToken);
        var instructor = seed("exam-owner@example.test", Role.INSTRUCTOR, true);
        var instructorToken = token(instructor);
        var questionId = createQuestion(instructorToken, subjectId, "Question before publish");
        var examBody = examBody(List.of(questionId), "  Midterm  ");
        var created = request(HttpMethod.POST, "/exams", instructorToken, examBody);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var examId = created.getBody().get("id").asText();
        assertThat(created.getBody().get("title").asText()).isEqualTo("Midterm");
        assertThat(created.getBody().get("state").asText()).isEqualTo("DRAFT");
        assertThat(created.getBody().get("revision").asInt()).isZero();

        assertThat(request(HttpMethod.POST, "/exams", instructorToken, examBody(List.of(UUID.randomUUID()), "Missing question"))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken, examBody(List.of(questionId, questionId), "Duplicate questions"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Invalid duration"), "durationSeconds", 59)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Duration over max"), "durationSeconds", 10801)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Invalid attempts"), "attemptLimit", 4)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Zero attempts"), "attemptLimit", 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Fractional attempts"), "attemptLimit", 1.5)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Invalid threshold"), "passingThreshold", new java.math.BigDecimal("100.01")))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "/exams", instructorToken,
                examBodyWith(examBody(List.of(questionId), "Threshold precision"), "passingThreshold", new java.math.BigDecimal("1.001")))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.GET, "/exams", instructorToken, null).getBody().get("total").asInt()).isEqualTo(1);
        var lowBoundary = examBody(List.of(questionId), "Low boundary");
        examBodyWith(lowBoundary, "durationSeconds", 60);
        examBodyWith(lowBoundary, "attemptLimit", 1);
        examBodyWith(lowBoundary, "passingThreshold", new java.math.BigDecimal("0.00"));
        assertThat(request(HttpMethod.POST, "/exams", instructorToken, lowBoundary).getStatusCode().value()).isEqualTo(201);
        var highBoundary = examBody(List.of(questionId), "High boundary");
        examBodyWith(highBoundary, "durationSeconds", 10800);
        examBodyWith(highBoundary, "attemptLimit", 3);
        examBodyWith(highBoundary, "passingThreshold", new java.math.BigDecimal("100.00"));
        assertThat(request(HttpMethod.POST, "/exams", instructorToken, highBoundary).getStatusCode().value()).isEqualTo(201);

        var other = seed("exam-other@example.test", Role.INSTRUCTOR, true);
        var otherToken = token(other);
        assertThat(request(HttpMethod.GET, "/exams/" + examId, otherToken, null).getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.PUT, "/exams/" + examId, otherToken,
                examUpdateBody(created.getBody(), "Foreign change")).getStatusCode().value()).isEqualTo(404);
        for (String denied : List.of(token(student), adminToken)) {
            assertThat(request(HttpMethod.GET, "/exams/" + examId, denied, null).getStatusCode().value()).isEqualTo(403);
            assertThat(request(HttpMethod.POST, "/exams", denied, examBody(List.of(questionId), "Denied"))
                    .getStatusCode().value()).isEqualTo(403);
        }

        var published = request(HttpMethod.POST, "/exams/" + examId + "/publish", instructorToken, Map.of("revision", 0));
        assertThat(published.getStatusCode().value()).isEqualTo(200);
        assertThat(published.getBody().get("state").asText()).isEqualTo("PUBLISHED");
        assertThat(published.getBody().get("revision").asInt()).isEqualTo(1);
        assertThat(published.getBody().get("questions").get(0).get("prompt").asText()).isEqualTo("Question before publish");
        assertThat(published.getBody().get("questions").get(0).get("options").get(0).get("correct").asBoolean()).isTrue();

        var changedOptions = Map.of("revision", 0, "subjectId", subjectId, "prompt", "Question after publish", "difficulty", "MEDIUM",
                "options", List.of(Map.of("label", "X", "text", "Changed answer", "correct", false),
                        Map.of("label", "Y", "text", "New correct answer", "correct", true)));
        assertThat(request(HttpMethod.PUT, "/questions/" + questionId, instructorToken, changedOptions)
                .getStatusCode().value()).isEqualTo(200);
        var afterEdit = request(HttpMethod.GET, "/exams/" + examId, instructorToken, null).getBody();
        assertThat(afterEdit.get("questions").get(0).get("prompt").asText()).isEqualTo("Question before publish");
        assertThat(afterEdit.get("questions").get(0).get("options").get(0).get("label").asText()).isEqualTo("A");
        assertThat(afterEdit.get("questions").get(0).get("options").get(0).get("text").asText()).isEqualTo("First answer");
        assertThat(afterEdit.get("questions").get(0).get("options").get(0).get("correct").asBoolean()).isTrue();
        assertThat(request(HttpMethod.PUT, "/exams/" + examId, instructorToken,
                examUpdateBody(published.getBody(), "Cannot change published")).getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.POST, "/exams/" + examId + "/publish", instructorToken, Map.of("revision", 1))
                .getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_question_snapshot WHERE exam_version_id=?", Integer.class,
                UUID.fromString(examId))).isEqualTo(1);
    }

    @Test
    void examDraftConcurrentUpdateHasOneRevisionWinner() throws Exception {
        var subjectId = createSubject(token(admin));
        var instructor = seed("exam-race@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var questionId = createQuestion(bearer, subjectId, "Race question");
        var exam = request(HttpMethod.POST, "/exams", bearer, examBody(List.of(questionId), "Race draft"));
        var examId = exam.getBody().get("id").asText();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return request(HttpMethod.PUT, "/exams/" + examId, bearer,
                        examUpdateBody(exam.getBody(), "Race title " + i)).getStatusCode().value();
            })).toList();
            gate.countDown();
            assertThat(List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(request(HttpMethod.GET, "/exams/" + examId, bearer, null).getBody().get("revision").asInt()).isEqualTo(1);
    }

    @Test
    void invalidPublishRollsBackStateAndSnapshot() {
        var subjectId = createSubject(token(admin));
        var instructor = seed("exam-invalid@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var questionId = createQuestion(bearer, subjectId, "Question");
        var exam = request(HttpMethod.POST, "/exams", bearer, examBody(List.of(questionId), "Draft"));
        var id = UUID.fromString(exam.getBody().get("id").asText());
        jdbc.update("DELETE FROM exam_draft_question WHERE exam_version_id=?", id);

        var rejected = request(HttpMethod.POST, "/exams/" + id + "/publish", bearer, Map.of("revision", 0));
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT state FROM exam_version WHERE id=?", String.class, id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT revision FROM exam_version WHERE id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_question_snapshot WHERE exam_version_id=?", Integer.class, id)).isZero();
    }

    @Test
    void closeRequiresPublishedOwnedExamAndIsIdempotentUnderConcurrency() throws Exception {
        var subjectId = createSubject(token(admin));
        var instructor = seed("exam-close@example.test", Role.INSTRUCTOR, true);
        var bearer = token(instructor);
        var questionId = createQuestion(bearer, subjectId, "Close question");
        var draft = request(HttpMethod.POST, "/exams", bearer, examBody(List.of(questionId), "Close test"));
        var examId = draft.getBody().get("id").asText();
        assertThat(request(HttpMethod.POST, "/exams/" + examId + "/close", bearer, null).getStatusCode().value()).isEqualTo(409);
        var published = request(HttpMethod.POST, "/exams/" + examId + "/publish", bearer, Map.of("revision", 0));
        assertThat(published.getStatusCode().value()).isEqualTo(200);

        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return request(HttpMethod.POST, "/exams/" + examId + "/close", bearer, null);
            })).toList();
            gate.countDown();
            var responses = List.of(jobs.get(0).get(15, TimeUnit.SECONDS), jobs.get(1).get(15, TimeUnit.SECONDS));
            assertThat(responses).allSatisfy(response -> {
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getBody().get("state").asText()).isEqualTo("CLOSED");
                assertThat(response.getBody().get("revision").asInt()).isEqualTo(2);
            });
        }
        var studentToken = token(student);
        var adminToken = token(admin);
        for (String denied : List.of(studentToken, adminToken)) {
            assertThat(request(HttpMethod.POST, "/exams/" + examId + "/close", denied, null).getStatusCode().value()).isEqualTo(403);
        }
        var other = seed("exam-close-other@example.test", Role.INSTRUCTOR, true);
        assertThat(request(HttpMethod.POST, "/exams/" + examId + "/close", token(other), null).getStatusCode().value()).isEqualTo(404);
        var repeated = request(HttpMethod.POST, "/exams/" + examId + "/close", bearer, null);
        assertThat(repeated.getStatusCode().value()).isEqualTo(200);
        assertThat(repeated.getBody().get("revision").asInt()).isEqualTo(2);
        assertThat(request(HttpMethod.POST, "/exams/" + examId + "/publish", bearer, Map.of("revision", 1))
                .getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.PUT, "/exams/" + examId, bearer,
                examUpdateBody(repeated.getBody(), "Closed update")).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_question_snapshot WHERE exam_version_id=?", Integer.class,
                UUID.fromString(examId))).isEqualTo(1);
    }

    private String createSubject(String adminToken) {
        var category = request(HttpMethod.POST, "/categories", adminToken, Map.of("name", "Question " + UUID.randomUUID()));
        var subject = request(HttpMethod.POST, "/subjects", adminToken,
                Map.of("categoryId", category.getBody().get("id").asText(), "name", "Subject " + UUID.randomUUID()));
        return subject.getBody().get("id").asText();
    }

    private String createQuestion(String instructorToken, String subjectId, String prompt) {
        return request(HttpMethod.POST, "/questions", instructorToken, questionBody(subjectId, prompt))
                .getBody().get("id").asText();
    }

    private static Map<String, Object> examBody(List<?> questionIds, String title) {
        return new HashMap<>(Map.of("title", title, "questionIds", questionIds, "durationSeconds", 1800,
                "attemptLimit", 2, "passingThreshold", new java.math.BigDecimal("66.67"), "shuffle", true));
    }

    private static Map<String, Object> examBodyWith(Map<String, Object> body, String field, Object value) {
        body.put(field, value);
        return body;
    }

    private static Map<String, Object> examUpdateBody(JsonNode exam, String title) {
        return new HashMap<>(Map.of("revision", exam.get("revision").asInt(), "title", title,
                "questionIds", List.of(exam.get("questionIds").get(0).asText()), "durationSeconds", 1800,
                "attemptLimit", 2, "passingThreshold", new java.math.BigDecimal("66.67"), "shuffle", true));
    }

    private static Map<String, Object> questionBody(Object subjectId, String prompt) {
        return new java.util.HashMap<>(Map.of("subjectId", subjectId, "prompt", prompt, "difficulty", "MEDIUM",
                "options", List.of(Map.of("label", "A", "text", "First answer", "correct", true),
                        Map.of("label", "B", "text", "Second answer", "correct", false))));
    }

    private static Map<String, Object> updateBody(Object subjectId, int revision, String prompt) {
        var body = questionBody(subjectId, prompt);
        body.put("revision", revision);
        return body;
    }

    private static String csvRow(String subjectId, String row) {
        return "subjectId,prompt,optionA,optionB,optionC,optionD,correctLabel\n" + subjectId + "," + row;
    }
}
