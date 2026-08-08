# Sprint 5: Privacy Controls & Data Export

## Metadata

- **Sprint Number**: 5
- **Estimated Time**: 3-4 hours
- **Complexity**: Medium
- **Dependencies**: Sprint 1-4 (all previous)

## Implementation Goals

1. Add visibility levels to activities (public, unlisted, private)
2. Filter federation delivery based on visibility
3. Filter outbox listing based on visibility
4. Export activities as GPX or JSON files

## What Gets Implemented

Activities gain a visibility field controlling who can see them and whether they're federated. Private activities stay local; unlisted activities appear on the profile but aren't pushed to followers; public activities federate normally. Users can export any of their activities as GPX or JSON downloads.

## Architecture Changes

### New Components

- **ExportResource**: REST endpoints for downloading activities as GPX/JSON
- **Database migration V5**: Adds `visibility` column to activities

### Visibility Model

| Visibility | Profile | Public Timeline | Federated | Segment Leaderboard |
|------------|---------|-----------------|-----------|---------------------|
| `public`   | ✅      | ✅              | ✅        | ✅                  |
| `unlisted` | ✅      | ❌              | ❌        | ✅                  |
| `private`  | ❌      | ❌              | ❌        | ❌                  |

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/activities/{id}/export/gpx` | Download activity as GPX file |
| GET | `/api/activities/{id}/export/json` | Download activity as JSON file |

## Implementation Steps

### Step 1: Database Migration

```sql
ALTER TABLE activities ADD COLUMN visibility VARCHAR(20) DEFAULT 'public';
UPDATE activities SET visibility = 'public' WHERE visibility IS NULL;
ALTER TABLE activities ALTER COLUMN visibility SET NOT NULL;
CREATE INDEX idx_activities_visibility ON activities(visibility);
```

### Step 2: Activity Entity Update

Add `visibility` field with enum-like validation (public/unlisted/private).

### Step 3: Visibility Checks

- `OutboxResource`: Filter outbox page by visibility (private excluded for remote viewers)
- `FederationDeliveryService`: Skip delivery for non-public activities
- `ActivityService`: Set default visibility on creation

### Step 4: Export Endpoints

- GPX export: Reconstruct GPX XML from stored track_data
- JSON export: Return full activity object as JSON download

## Next Steps

- ✅ You've completed Sprint 5!
- 🎯 **Next**: Sprint 6: Authentication & Authorization
