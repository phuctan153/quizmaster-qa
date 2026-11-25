CREATE TABLE attempt (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES exam_assignment(id) ON DELETE RESTRICT,
    exam_version_id UUID NOT NULL REFERENCES exam_version(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES account(id) ON DELETE RESTRICT,
    attempt_number SMALLINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ NOT NULL,
    finalized_at TIMESTAMPTZ,
    CONSTRAINT attempt_assignment_number_unique UNIQUE (assignment_id, attempt_number),
    CHECK (attempt_number BETWEEN 1 AND 3),
    CHECK (state IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    CHECK (deadline > started_at),
    CHECK ((state = 'IN_PROGRESS' AND finalized_at IS NULL) OR (state <> 'IN_PROGRESS' AND finalized_at IS NOT NULL))
);

CREATE UNIQUE INDEX attempt_one_active_per_assignment_idx ON attempt(assignment_id) WHERE state='IN_PROGRESS';
CREATE INDEX attempt_student_started_idx ON attempt(student_id, started_at, id);

CREATE TABLE attempt_question (
    attempt_id UUID NOT NULL REFERENCES attempt(id) ON DELETE RESTRICT,
    exam_question_id UUID NOT NULL REFERENCES exam_question_snapshot(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    PRIMARY KEY (attempt_id, exam_question_id),
    UNIQUE (attempt_id, position),
    CHECK (position >= 0)
);

CREATE TABLE answer (
    attempt_id UUID NOT NULL,
    exam_question_id UUID NOT NULL,
    selected_option_id UUID,
    revision INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ,
    PRIMARY KEY (attempt_id, exam_question_id),
    FOREIGN KEY (attempt_id, exam_question_id) REFERENCES attempt_question(attempt_id, exam_question_id) ON DELETE RESTRICT,
    CHECK (revision >= 0),
    CHECK ((revision = 0 AND updated_at IS NULL) OR (revision > 0 AND updated_at IS NOT NULL))
);

CREATE TABLE exam_result (
    attempt_id UUID PRIMARY KEY REFERENCES attempt(id) ON DELETE RESTRICT,
    correct_count INTEGER NOT NULL,
    total_count INTEGER NOT NULL,
    score NUMERIC(5,2) NOT NULL,
    passed BOOLEAN NOT NULL,
    finalized_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(16) NOT NULL,
    CHECK (total_count > 0 AND correct_count BETWEEN 0 AND total_count),
    CHECK (score BETWEEN 0 AND 100),
    CHECK (reason IN ('SUBMITTED', 'EXPIRED'))
);
