CREATE TABLE account (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (email = lower(btrim(email)) AND length(email) > 0)
);
