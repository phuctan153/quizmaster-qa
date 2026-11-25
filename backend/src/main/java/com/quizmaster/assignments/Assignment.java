package com.quizmaster.assignments;

import java.time.Instant;
import java.util.UUID;

public record Assignment(UUID id, UUID examId, UUID studentId, int allocatedCount, Instant createdAt) {}
