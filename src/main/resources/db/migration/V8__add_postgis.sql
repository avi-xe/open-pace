-- V8: Add PostGIS geospatial support
-- Stores simplified tracks as LineString and start/end as Point geometry.

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Add geometry columns to activities table
-- SRID 4326 = WGS84 (lat/lon coordinate system)
ALTER TABLE activities ADD COLUMN track_line geometry(LineString, 4326);
ALTER TABLE activities ADD COLUMN start_point geometry(Point, 4326);
ALTER TABLE activities ADD COLUMN end_point geometry(Point, 4326);

-- Spatial indexes for efficient geospatial queries
-- GIST indexes enable bounding box and distance queries
CREATE INDEX idx_activities_track_line ON activities USING GIST (track_line);
CREATE INDEX idx_activities_start_point ON activities USING GIST (start_point);
CREATE INDEX idx_activities_end_point ON activities USING GIST (end_point);
