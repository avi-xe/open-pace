-- Sprint 4: Segments & Leaderboards

-- Segments: named stretches of road/trail
CREATE TABLE segments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    activity_type VARCHAR(50) NOT NULL,
    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,
    end_lat DOUBLE PRECISION NOT NULL,
    end_lon DOUBLE PRECISION NOT NULL,
    distance DOUBLE PRECISION NOT NULL,
    elevation_gain DOUBLE PRECISION DEFAULT 0,
    elevation_loss DOUBLE PRECISION DEFAULT 0,
    created_by BIGINT REFERENCES actors(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_segments_activity_type ON segments(activity_type);
CREATE INDEX idx_segments_start ON segments(start_lat, start_lon);

-- Segment efforts: an activity's completion of a segment
CREATE TABLE segment_efforts (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT REFERENCES segments(id) NOT NULL,
    activity_id BIGINT REFERENCES activities(id) NOT NULL,
    actor_id BIGINT REFERENCES actors(id) NOT NULL,
    elapsed_time BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(segment_id, activity_id)
);

CREATE INDEX idx_segment_efforts_segment ON segment_efforts(segment_id);
CREATE INDEX idx_segment_efforts_actor ON segment_efforts(actor_id);
CREATE INDEX idx_segment_efforts_time ON segment_efforts(segment_id, elapsed_time);
