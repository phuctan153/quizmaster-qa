CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 120),
    CHECK (length(normalized_name) BETWEEN 1 AND 120)
);

CREATE TABLE subject (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 120),
    CHECK (length(normalized_name) BETWEEN 1 AND 120),
    CONSTRAINT subject_category_normalized_name_unique UNIQUE (category_id, normalized_name)
);

CREATE INDEX subject_category_id_id_idx ON subject(category_id, id);
