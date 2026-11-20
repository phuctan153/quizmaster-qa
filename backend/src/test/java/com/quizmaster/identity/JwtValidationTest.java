package com.quizmaster.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationTest {
    private final SecurityConfig config = new SecurityConfig();
    private final Instant now = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void expiryIsRejectedAtExactDeadlineWithoutClockSkew() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        var key = config.jwtKey(Base64.getEncoder().encodeToString(bytes));
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var token = new JwtService(config.jwtEncoder(key), clock)
                .issue(new AccountView(UUID.randomUUID(), "clock@example.test", Role.STUDENT, true)).accessToken();
        assertThat(config.jwtDecoder(key, Clock.fixed(now.plusSeconds(899), ZoneOffset.UTC)).decode(token).getSubject()).isNotBlank();
        assertThatThrownBy(() -> config.jwtDecoder(key, Clock.fixed(now.plusSeconds(900), ZoneOffset.UTC)).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedAndShortSecretsFailClosedWithoutEchoingInput() {
        assertThatThrownBy(() -> config.jwtKey("not-base64!"))
                .isInstanceOf(IllegalStateException.class).hasMessage("JWT secret must be base64 encoded");
        assertThatThrownBy(() -> config.jwtKey(Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(IllegalStateException.class).hasMessage("JWT secret must contain at least 32 decoded bytes");
    }

    @Test
    void credentialRecordsNeverPrintPasswords() {
        String password = UUID.randomUUID().toString();
        assertThat(new AccountRequests.Login("student@example.test", password).toString()).doesNotContain(password);
        assertThat(new AccountRequests.Create("student@example.test", password, Role.STUDENT, true).toString()).doesNotContain(password);
        assertThat(new JwtService.Token(password, "Bearer", 900).toString()).doesNotContain(password);
    }
}
