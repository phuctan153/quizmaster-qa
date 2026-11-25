package com.quizmaster.taxonomy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;

public final class TaxonomyRequests {
    private TaxonomyRequests() {}
    static String normalize(String value) { return value == null ? null : value.trim().toLowerCase(Locale.ROOT); }
    private static String trim(String value) { return value == null ? null : value.trim(); }

    public record Name(@NotBlank @Size(max = 120) String name) {
        public Name { name = trim(name); }
    }
    public record SubjectInput(@NotNull UUID categoryId, @NotBlank @Size(max = 120) String name) {
        public SubjectInput { name = trim(name); }
    }
}
