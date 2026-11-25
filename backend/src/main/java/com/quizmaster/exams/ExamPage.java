package com.quizmaster.exams;

import java.util.List;

public record ExamPage(List<Exam> items, int page, int size, long total) {}
