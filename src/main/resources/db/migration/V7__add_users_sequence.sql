-- V7: Fix missing users sequence for PanacheEntity
-- The users table was created with BIGSERIAL id in V6, but Hibernate
-- expects a sequence named users_SEQ for PanacheEntity's @GeneratedValue.

CREATE SEQUENCE IF NOT EXISTS users_SEQ START WITH 1 INCREMENT BY 50;
