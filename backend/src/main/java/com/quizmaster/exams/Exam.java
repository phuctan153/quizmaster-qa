package com.quizmaster.exams;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Exam(UUID id, UUID familyId, int versionNumber, UUID ownerId, String title,
                   ExamState state, int durationSeconds, int attemptLimit, BigDecimal passingThreshold,
                   boolean shuffle, int revision, List<UUID> questionIds, List<QuestionSnapshot> questions) {
    public Exam {
        questionIds = List.copyOf(questionIds);
        questions = List.copyOf(questions);
    }
    public record QuestionSnapshot(UUID id, UUID sourceQuestionId, int position, String prompt,
                                   String difficulty, List<OptionSnapshot> options) {
        public QuestionSnapshot { options = List.copyOf(options); }
    }
    public record OptionSnapshot(UUID id, String label, String text, boolean correct) {}
}
