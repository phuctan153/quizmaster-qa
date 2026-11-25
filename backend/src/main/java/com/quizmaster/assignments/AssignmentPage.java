package com.quizmaster.assignments;

import java.util.List;

public record AssignmentPage<T>(List<T> items, int page, int size, long total) {
    public AssignmentPage { items = List.copyOf(items); }
}
