package com.quizmaster.attempts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptView(UUID id, UUID examId, int attemptNumber, AttemptState state, Instant startedAt,
                          Instant deadline, Instant finalizedAt, List<Question> questions) {
    public AttemptView { questions = List.copyOf(questions); }

    public record Question(UUID id, String prompt, List<Option> options, UUID selectedOptionId, int revision) {
        public Question { options = List.copyOf(options); }
    }
    public record Option(UUID id, String label, String text) {}
}
