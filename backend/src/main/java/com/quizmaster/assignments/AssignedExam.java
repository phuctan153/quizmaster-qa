package com.quizmaster.assignments;

import com.quizmaster.exams.ExamState;
import java.util.UUID;

public record AssignedExam(UUID examId, String title, ExamState state, int durationSeconds,
                           int attemptLimit, int attemptsUsed) {}
