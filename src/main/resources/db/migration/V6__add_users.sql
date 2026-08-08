-- Sprint 6: Authentication & Authorization

-- Users table for authentication
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT true,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Link actors to users
ALTER TABLE actors ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_actors_user_id ON actors(user_id);
