-- Every job post we keep, from any source.
CREATE TABLE jobs (
    id              BIGSERIAL PRIMARY KEY,
    source          TEXT        NOT NULL,          -- adzuna / greenhouse / lever / ashby
    source_job_id   TEXT        NOT NULL,          -- ID given by the source
    title           TEXT        NOT NULL,
    company         TEXT        NOT NULL,
    location_raw    TEXT,                          -- exactly as the source gave it
    description     TEXT,                          -- HTML stripped, trimmed
    apply_url       TEXT,
    posted_at       TIMESTAMPTZ,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- normalized company|title|city; the DB itself rejects duplicates
    dedupe_key      TEXT        NOT NULL,

    -- filled by the LLM tagger; NULL role_tag = not tagged yet
    role_tag        TEXT,
    city_tag        TEXT,
    is_fresher      BOOLEAN,
    tagged_at       TIMESTAMPTZ,
    tag_attempts    INT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_jobs_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT uq_jobs_source_id  UNIQUE (source, source_job_id)
);

CREATE INDEX idx_jobs_role      ON jobs (role_tag);
CREATE INDEX idx_jobs_city      ON jobs (city_tag);
CREATE INDEX idx_jobs_fresher   ON jobs (is_fresher);
CREATE INDEX idx_jobs_posted_at ON jobs (posted_at DESC);

-- One row per refresh run. Gives real numbers for the report and cost per 1,000 posts.
CREATE TABLE run_log (
    id              BIGSERIAL PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    status          TEXT        NOT NULL DEFAULT 'RUNNING',  -- RUNNING / SUCCESS / FAILED
    fetched         INT         NOT NULL DEFAULT 0,
    new_jobs        INT         NOT NULL DEFAULT 0,
    duplicates      INT         NOT NULL DEFAULT 0,
    tagged          INT         NOT NULL DEFAULT 0,
    tag_failed      INT         NOT NULL DEFAULT 0,
    input_tokens    BIGINT      NOT NULL DEFAULT 0,
    output_tokens   BIGINT      NOT NULL DEFAULT 0,
    llm_model       TEXT,
    cost_usd        NUMERIC(12, 6) NOT NULL DEFAULT 0,
    error           TEXT
);
