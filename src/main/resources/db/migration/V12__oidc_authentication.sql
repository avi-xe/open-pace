-- Sprint 9: OIDC Authentication

-- Create external_identity table for OIDC provider mapping
CREATE TABLE external_identity (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    username VARCHAR(255),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);

CREATE INDEX idx_external_identity_provider ON external_identity(provider);
CREATE INDEX idx_external_identity_user_id ON external_identity(user_id);
CREATE INDEX idx_external_identity_email ON external_identity(email);

-- Drop password column from users (OIDC handles auth externally)
ALTER TABLE users DROP COLUMN password;

-- Add display_name to users for OIDC-sourced profiles
ALTER TABLE users ADD COLUMN display_name VARCHAR(255);
