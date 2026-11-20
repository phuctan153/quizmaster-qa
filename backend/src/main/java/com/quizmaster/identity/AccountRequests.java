package com.quizmaster.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class AccountRequests {
    private AccountRequests() {}

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotNull @Size(min = 12, max = 128) String password) {
        public Login { email = normalize(email); }
        @Override public String toString() { return "Login[redacted]"; }
    }

    public record Create(@NotBlank @Email @Size(max = 254) String email,
                         @NotNull @Size(min = 12, max = 128) String password,
                         @NotNull Role role, @NotNull Boolean active) {
        public Create { email = normalize(email); }
        @Override public String toString() { return "Create[redacted]"; }
    }

    public record Status(@NotNull Boolean active) {}
}
