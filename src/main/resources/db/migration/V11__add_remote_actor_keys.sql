-- Sprint 8.5: Cache remote actor public keys for signature verification

CREATE TABLE remote_actor_key (
    id BIGSERIAL PRIMARY KEY,
    actor_url TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_remote_actor_key_url ON remote_actor_key(actor_url);
