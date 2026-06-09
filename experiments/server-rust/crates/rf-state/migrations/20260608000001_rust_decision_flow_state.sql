-- 2026-06-08 Phase 6 — Postgres-backed decision flow state.
--
-- Rust-side mirror of Java `nd_decision_flow_state` (V5.19.0). Different
-- database / different engine — two state stores run side by side, each
-- managing its own flows. Java executor doesn't read or write this table
-- (and vice versa).
--
-- Lock primitive differs: Java uses timestamp CAS
-- (`locked_until <= NOW()`); we use real `pg_try_advisory_lock` for the
-- per-flow-run CAS and `FOR UPDATE SKIP LOCKED` for the recovery sweep.
-- The `locked_by/locked_at/locked_until` columns are kept for parity /
-- observability only — the authoritative lock is the advisory lock.
--
-- jsonb over MEDIUMTEXT so we can index/query fields (e.g.
-- `row_vars->'applicant'->'age'`). TIMESTAMPTZ over DATETIME for tz safety.

CREATE TABLE rust_decision_flow_state (
    id                  BIGSERIAL    PRIMARY KEY,
    flow_id             VARCHAR(200) NOT NULL,
    flow_run_id         VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(64),
    order_no            VARCHAR(64),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','RUNNING','PENDING_ASYNC','WAITING_CALLBACK','COMPLETED','FAILED')),
    current_node_id     VARCHAR(200),
    current_node_type   VARCHAR(40),
    next_retry_at       TIMESTAMPTZ,
    wait_ref            VARCHAR(200),
    wait_type           VARCHAR(20)
                        CHECK (wait_type IS NULL OR wait_type IN ('USER_TASK','ASYNC_DATA','ASYNC_TASK')),
    flow_xml_version    VARCHAR(64),
    row_vars            JSONB,
    row_entity_snapshot JSONB,
    output_model        JSONB,
    progress            DOUBLE PRECISION DEFAULT 0,
    error_message       TEXT,
    locked_by           VARCHAR(64),
    locked_at           TIMESTAMPTZ,
    locked_until        TIMESTAMPTZ,
    retry_count         INT          DEFAULT 0,
    total_execution_ms  BIGINT       DEFAULT 0,
    fireable_rules      INT          DEFAULT 0,
    matched_rules       INT          DEFAULT 0,
    create_time         TIMESTAMPTZ  DEFAULT NOW(),
    update_time         TIMESTAMPTZ  DEFAULT NOW(),
    CONSTRAINT uk_flow_run_id UNIQUE (flow_run_id)
);

-- Recovery sweep: only status that need re-driving
CREATE INDEX idx_status_next_retry
    ON rust_decision_flow_state (status, next_retry_at)
    WHERE status IN ('PENDING_ASYNC','WAITING_CALLBACK');

-- jsonb GIN for ad-hoc queries on applicant/order fields
CREATE INDEX idx_row_vars_applicant
    ON rust_decision_flow_state
    USING GIN ((row_vars->'applicant'));

-- keep update_time fresh
CREATE OR REPLACE FUNCTION rust_decision_flow_state_touch()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rust_decision_flow_state_touch
    BEFORE UPDATE ON rust_decision_flow_state
    FOR EACH ROW
    EXECUTE FUNCTION rust_decision_flow_state_touch();
