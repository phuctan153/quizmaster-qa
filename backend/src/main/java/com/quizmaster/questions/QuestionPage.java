package com.quizmaster.questions;

import java.util.List;

public record QuestionPage(List<Question> items, int page, int size, long total) {}
