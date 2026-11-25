CREATE TABLE exam_version (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE RESTRICT,
    title VARCHAR(120) NOT NULL,
    state VARCHAR(16) NOT NULL,
    duration_seconds INTEGER NOT NULL,
    attempt_limit SMALLINT NOT NULL,
    passing_threshold NUMERIC(5,2) NOT NULL,
    shuffle BOOLEAN NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT exam_family_version_unique UNIQUE (family_id, version_number),
    CHECK (version_number > 0),
    CHECK (title = btrim(title) AND length(title) BETWEEN 1 AND 120),
    CHECK (state IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CHECK (duration_seconds BETWEEN 60 AND 10800),
    CHECK (attempt_limit BETWEEN 1 AND 3),
    CHECK (passing_threshold BETWEEN 0 AND 100),
    CHECK (revision >= 0),
    CHECK ((state = 'DRAFT' AND published_at IS NULL) OR (state <> 'DRAFT' AND published_at IS NOT NULL))
);

CREATE INDEX exam_version_owner_state_id_idx ON exam_version(owner_id, state, id);

CREATE TABLE exam_draft_question (
    exam_version_id UUID NOT NULL REFERENCES exam_version(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    PRIMARY KEY (exam_version_id, question_id),
    UNIQUE (exam_version_id, position),
    CHECK (position >= 0)
);

CREATE TABLE exam_question_snapshot (
    id UUID PRIMARY KEY,
    exam_version_id UUID NOT NULL REFERENCES exam_version(id) ON DELETE RESTRICT,
    source_question_id UUID NOT NULL REFERENCES question(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    prompt VARCHAR(2000) NOT NULL,
    difficulty VARCHAR(8),
    UNIQUE (exam_version_id, source_question_id),
    UNIQUE (exam_version_id, position),
    CHECK (position >= 0),
    CHECK (prompt = btrim(prompt) AND length(prompt) BETWEEN 1 AND 2000),
    CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD'))
);

CREATE TABLE exam_option_snapshot (
    id UUID PRIMARY KEY,
    exam_question_id UUID NOT NULL REFERENCES exam_question_snapshot(id) ON DELETE RESTRICT,
    source_option_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    label VARCHAR(20) NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL,
    UNIQUE (exam_question_id, position),
    CHECK (position BETWEEN 0 AND 5),
    CHECK (label = btrim(label) AND length(label) BETWEEN 1 AND 20),
    CHECK (option_text = btrim(option_text) AND length(option_text) BETWEEN 1 AND 500)
);
