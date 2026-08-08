-- Sprint 5: Privacy Controls & Data Export

-- Visibility levels: public, unlisted, private
ALTER TABLE activities ADD COLUMN visibility VARCHAR(20) DEFAULT 'public';
UPDATE activities SET visibility = 'public' WHERE visibility IS NULL;
ALTER TABLE activities ALTER COLUMN visibility SET NOT NULL;
CREATE INDEX idx_activities_visibility ON activities(visibility);
