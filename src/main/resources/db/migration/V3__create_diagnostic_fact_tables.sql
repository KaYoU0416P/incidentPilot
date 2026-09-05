CREATE TABLE incident_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_key TEXT NOT NULL UNIQUE,
    service_name TEXT NOT NULL,
    summary TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_incident_service_time ON incident_record (service_name, occurred_at DESC);

CREATE TABLE deployment_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name TEXT NOT NULL,
    version TEXT NOT NULL,
    deployed_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL
);

CREATE INDEX idx_deployment_service_time ON deployment_record (service_name, deployed_at DESC);

CREATE TABLE service_status_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name TEXT NOT NULL,
    status TEXT NOT NULL,
    detail TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_service_status_time ON service_status_snapshot (service_name, observed_at DESC);

CREATE TABLE change_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name TEXT NOT NULL,
    change_type TEXT NOT NULL,
    summary TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_change_service_time ON change_record (service_name, changed_at DESC);
