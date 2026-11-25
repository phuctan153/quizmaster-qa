package com.quizmaster.questions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class QuestionRequests {
    private QuestionRequests() {}

    static String trim(String value) { return value == null ? null : value.trim(); }

    public record OptionInput(@NotBlank @Size(max = 20) String label,
                              @NotBlank @Size(max = 500) String text,
                              @NotNull Boolean correct) {
        public OptionInput { label = trim(label); text = trim(text); }
    }

    public record Input(@NotNull UUID subjectId,
                        @NotBlank @Size(max = 2000) String prompt,
                        Difficulty difficulty,
                        @NotNull @Size(min = 2, max = 6) List<@Valid OptionInput> options) {
        public Input { prompt = trim(prompt); }
    }

    public record Update(@NotNull @Min(0) Integer revision,
                         @NotNull UUID subjectId,
                         @NotBlank @Size(max = 2000) String prompt,
                         Difficulty difficulty,
                         @NotNull @Size(min = 2, max = 6) List<@Valid OptionInput> options) {
        public Update { prompt = trim(prompt); }
        Input input() { return new Input(subjectId, prompt, difficulty, options); }
    }
}
