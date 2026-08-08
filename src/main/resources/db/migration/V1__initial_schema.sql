-- Sprint 1: Initial schema for Open Pace

-- Actors table
CREATE TABLE actors (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_actors_username ON actors(username);

-- Activities table
CREATE TABLE activities (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES actors(id) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    activity_id VARCHAR(500) UNIQUE NOT NULL,
    object_type VARCHAR(100),
    object_content TEXT,
    object_id VARCHAR(500),
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activities_actor_id ON activities(actor_id);
CREATE INDEX idx_activities_published_at ON activities(published_at DESC);
CREATE INDEX idx_activities_activity_id ON activities(activity_id);
CREATE INDEX idx_activities_object_id ON activities(object_id);

-- Followers table
CREATE TABLE followers (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES actors(id) NOT NULL,
    follower_actor_id BIGINT REFERENCES actors(id),
    follower_actor_url VARCHAR(500) NOT NULL,
    follower_inbox VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(actor_id, follower_actor_url)
);

CREATE INDEX idx_followers_actor_id ON followers(actor_id);
CREATE INDEX idx_followers_follower_url ON followers(follower_actor_url);
