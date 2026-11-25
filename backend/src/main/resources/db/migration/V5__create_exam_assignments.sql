CREATE TABLE exam_assignment (
    id UUID PRIMARY KEY,
    exam_version_id UUID NOT NULL REFERENCES exam_version(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES account(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    allocated_count SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT exam_assignment_version_student_unique UNIQUE (exam_version_id, student_id),
    CHECK (allocated_count BETWEEN 0 AND 3)
);

CREATE INDEX exam_assignment_student_created_idx ON exam_assignment(student_id, created_at, id);
