package com.quizmaster.questions;

import java.util.List;
import java.util.UUID;

public record Question(UUID id, UUID ownerId, UUID subjectId, String prompt,
                       Difficulty difficulty, int revision, List<Option> options) {
    public Question { options = List.copyOf(options); }
    public record Option(UUID id, String label, String text, boolean correct) {}
}
