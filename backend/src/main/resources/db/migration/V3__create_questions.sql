CREATE TABLE question (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE RESTRICT,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE RESTRICT,
    prompt VARCHAR(2000) NOT NULL,
    difficulty VARCHAR(8),
    revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (prompt = btrim(prompt) AND length(prompt) BETWEEN 1 AND 2000),
    CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CHECK (revision >= 0)
);

CREATE INDEX question_owner_subject_id_idx ON question(owner_id, subject_id, id);

CREATE TABLE question_option (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL,
    label VARCHAR(20) NOT NULL,
    normalized_label VARCHAR(20) NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    normalized_text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL,
    CHECK (position BETWEEN 0 AND 5),
    CHECK (label = btrim(label) AND length(label) BETWEEN 1 AND 20),
    CHECK (option_text = btrim(option_text) AND length(option_text) BETWEEN 1 AND 500),
    UNIQUE (question_id, position),
    UNIQUE (question_id, normalized_label),
    UNIQUE (question_id, normalized_text)
);

CREATE UNIQUE INDEX question_option_single_correct_idx ON question_option(question_id) WHERE correct;
