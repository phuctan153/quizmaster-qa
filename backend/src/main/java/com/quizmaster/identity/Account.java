package com.quizmaster.identity;

import java.util.UUID;

// Internal persistence model; never serialize directly to a response.
record Account(UUID id, String email, String passwordHash, Role role, boolean active) {
    AccountView view() {
        return new AccountView(id, email, role, active);
    }
}
