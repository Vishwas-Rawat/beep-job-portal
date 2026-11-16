-- Split "seen before" from "real duplicate", and keep a per-source breakdown.
ALTER TABLE run_log ADD COLUMN already_stored INT  NOT NULL DEFAULT 0;  -- same source + id as an earlier run
ALTER TABLE run_log ADD COLUMN source_counts  TEXT;                     -- e.g. "greenhouse=640, lever=300"
