-- Store raw GPX XML for re-export and re-parsing
ALTER TABLE activities ADD COLUMN gpx_data TEXT;

-- Store parsed track data as JSONB for fast queries
-- Structure: { "points": [{lat, lon, ele, time, speed}], "summary": {distance, duration, pace, elevationGain, elevationLoss} }
ALTER TABLE activities ADD COLUMN track_data JSONB;

-- GIN index for querying track data properties
CREATE INDEX idx_activities_track_data ON activities USING GIN (track_data);
