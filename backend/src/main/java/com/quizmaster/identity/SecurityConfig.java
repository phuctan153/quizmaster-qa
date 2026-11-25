package com.quizmaster.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.quizmaster.shared.ApiError;
import com.quizmaster.shared.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecretKey jwtKey(@Value("${quizmaster.jwt-secret}") String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret must be base64 encoded");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 decoded bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> contract = jwt -> {
            boolean valid = jwt.getAudience().contains("quizmaster-api")
                    && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && clock.instant().isBefore(jwt.getExpiresAt())
                    && !jwt.getIssuedAt().isAfter(clock.instant())
                    && jwt.getSubject() != null;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,
                new JwtIssuerValidator("quizmaster-qa"), contract));
        return decoder;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccountService accounts, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/categories", "/api/v1/subjects").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/categories", "/api/v1/subjects").hasAnyRole("ADMIN", "INSTRUCTOR")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/students").hasRole("INSTRUCTOR")
                        .requestMatchers("/api/v1/assignments").hasRole("STUDENT")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/exams/*/attempts").hasRole("STUDENT")
                        .requestMatchers("/api/v1/attempts/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/questions/**").hasRole("INSTRUCTOR")
                        .requestMatchers("/api/v1/exams", "/api/v1/exams/**").hasRole("INSTRUCTOR")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeError(mapper, response, 401))
                        .accessDeniedHandler((request, response, ex) -> writeError(mapper, response, 403)))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((request, response, ex) -> writeError(mapper, response,
                                ex instanceof OAuth2AuthenticationException failure
                                        && failure.getError().getErrorCode().equals("temporarily_unavailable") ? 503 : 401))
                        .accessDeniedHandler((request, response, ex) -> writeError(mapper, response, 403))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            try {
                                var account = accounts.activeIdentity(UUID.fromString(token.getSubject()));
                                return new UsernamePasswordAuthenticationToken(account, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
                            } catch (IllegalArgumentException | ApiException ex) {
                                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                            } catch (org.springframework.dao.DataAccessException ex) {
                                throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"));
                            }
                        })))
                .build();
    }

    private static void writeError(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response, int status)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        var error = switch (status) {
            case 401 -> new ApiError("UNAUTHENTICATED", "Authentication required");
            case 503 -> new ApiError("SERVICE_UNAVAILABLE", "Service temporarily unavailable");
            default -> new ApiError("FORBIDDEN", "Access denied");
        };
        mapper.writeValue(response.getOutputStream(), error);
    }
}
