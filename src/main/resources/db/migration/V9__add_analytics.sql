-- Sprint 8: Activity Analytics & Personal Records
-- Adds tables for PRs, splits, pace zones, comparisons

-- Personal Records
CREATE TABLE personal_record (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES actors(id),
    activity_type VARCHAR(50) NOT NULL,
    distance_label VARCHAR(20) NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    elapsed_time BIGINT NOT NULL,
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    achieved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_actor_type ON personal_record(actor_id, activity_type);
CREATE INDEX idx_pr_distance ON personal_record(distance_label);
CREATE UNIQUE INDEX idx_pr_unique ON personal_record(actor_id, activity_type, distance_label);

-- Activity Splits (per km/mile)
CREATE TABLE activity_split (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    split_number INT NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    elapsed_time BIGINT NOT NULL,
    pace DOUBLE PRECISION NOT NULL,
    elevation_gain DOUBLE PRECISION DEFAULT 0,
    elevation_loss DOUBLE PRECISION DEFAULT 0,
    average_heart_rate INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_split_activity ON activity_split(activity_id);
CREATE UNIQUE INDEX idx_split_unique ON activity_split(activity_id, split_number);

-- Activity Pace Zones
CREATE TABLE activity_pace_zone (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    zone_number INT NOT NULL,
    zone_name VARCHAR(20) NOT NULL,
    time_in_seconds BIGINT NOT NULL,
    percentage DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pace_zone_activity ON activity_pace_zone(activity_id);
CREATE UNIQUE INDEX idx_pace_zone_unique ON activity_pace_zone(activity_id, zone_number);

-- Activity Comparisons (vs user average)
CREATE TABLE activity_comparison (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    metric_name VARCHAR(50) NOT NULL,
    activity_value DOUBLE PRECISION NOT NULL,
    user_average DOUBLE PRECISION NOT NULL,
    percent_diff DOUBLE PRECISION NOT NULL,
    is_improvement BOOLEAN NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comparison_activity ON activity_comparison(activity_id);
CREATE UNIQUE INDEX idx_comparison_unique ON activity_comparison(activity_id, metric_name);
