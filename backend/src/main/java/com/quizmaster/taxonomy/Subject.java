package com.quizmaster.taxonomy;

import java.util.UUID;

public record Subject(UUID id, UUID categoryId, String name) {}
