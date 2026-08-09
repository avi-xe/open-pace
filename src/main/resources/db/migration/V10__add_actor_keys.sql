-- Sprint 8.5: Add RSA key pairs for HTTP Signature federation

ALTER TABLE actors
    ADD COLUMN public_key TEXT,
    ADD COLUMN private_key TEXT;
