package com.quizmaster.identity;

import java.util.UUID;

public record AccountView(UUID id, String email, Role role, boolean active) {}
