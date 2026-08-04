-- Agent 指标采集表（JPA auto DDL 会自动创建，此文件仅作参考）
-- P0-1: 量化指标采集

CREATE TABLE IF NOT EXISTS agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    metric_type VARCHAR(32) NOT NULL,  -- TOKEN / LATENCY / COMPRESSION / TOOL_CALL
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_metrics_session ON agent_metrics(session_id);
CREATE INDEX IF NOT EXISTS idx_metrics_type ON agent_metrics(metric_type);
CREATE INDEX IF NOT EXISTS idx_metrics_created ON agent_metrics(created_at);

-- NvcEvaluationEntity 新增 degraded 字段（JPA auto DDL 会自动添加）
-- ALTER TABLE nvc_evaluation ADD COLUMN IF NOT EXISTS degraded BOOLEAN DEFAULT FALSE;
