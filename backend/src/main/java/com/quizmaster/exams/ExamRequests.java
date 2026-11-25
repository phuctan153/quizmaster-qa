package com.quizmaster.exams;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ExamRequests {
    private ExamRequests() {}
    private static String trim(String value) { return value == null ? null : value.trim(); }

    public record Input(@NotBlank @Size(max = 120) String title,
                        @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> questionIds,
                        @NotNull @DecimalMin("60") @DecimalMax("10800") @Digits(integer = 5, fraction = 0) BigDecimal durationSeconds,
                        @NotNull @DecimalMin("1") @DecimalMax("3") @Digits(integer = 1, fraction = 0) BigDecimal attemptLimit,
                        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal passingThreshold,
                        @NotNull Boolean shuffle) {
        public Input { title = trim(title); }
    }

    public record Update(@NotNull @Min(0) Integer revision,
                         @NotBlank @Size(max = 120) String title,
                         @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> questionIds,
                         @NotNull @DecimalMin("60") @DecimalMax("10800") @Digits(integer = 5, fraction = 0) BigDecimal durationSeconds,
                         @NotNull @DecimalMin("1") @DecimalMax("3") @Digits(integer = 1, fraction = 0) BigDecimal attemptLimit,
                         @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal passingThreshold,
                         @NotNull Boolean shuffle) {
        public Update { title = trim(title); }
        Input input() { return new Input(title, questionIds, durationSeconds, attemptLimit, passingThreshold, shuffle); }
    }

    public record Revision(@NotNull @Min(0) Integer revision) {}
}
