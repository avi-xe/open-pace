-- Create the embedded test user in the users table
-- so that User.findByUsername("testuser") works in tests
INSERT INTO users (id, username, email, display_name, verified, role, created_at, updated_at)
VALUES (9999, 'testuser', 'testuser@test.com', 'Test User', true, 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Create linked actor for the test user
-- so that Actor.findByUsername("testuser") works in tests
INSERT INTO actors (id, username, name, user_id)
VALUES (9999, 'testuser', 'Test User', 9999);
