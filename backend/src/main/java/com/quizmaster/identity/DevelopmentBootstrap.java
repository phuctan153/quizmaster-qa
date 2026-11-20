package com.quizmaster.identity;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Explicit opt-in for an empty local environment, never a default account. */
@Configuration
@Profile("bootstrap")
public class DevelopmentBootstrap {
    @Bean
    ApplicationRunner bootstrap(AccountService accounts, AccountRepository repository, Validator validator,
            @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password) {
        return args -> {
            var request = new AccountRequests.Create(email, password, Role.ADMIN, true);
            if (!validator.validate(request).isEmpty()) {
                throw new IllegalStateException("Bootstrap administrator configuration is invalid");
            }
            if (repository.count() != 0) {
                throw new IllegalStateException("Bootstrap requires an empty account table; disable the bootstrap profile");
            }
            accounts.create(request);
        };
    }
}
