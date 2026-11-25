package com.quizmaster.questions;

import java.util.List;
import java.util.UUID;

public record QuestionImportResult(int imported, List<UUID> questionIds) {
    public QuestionImportResult { questionIds = List.copyOf(questionIds); }
}
