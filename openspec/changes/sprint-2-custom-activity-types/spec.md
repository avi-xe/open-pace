# Sprint 2 Spec: Custom Activity Types

## Acceptance Criteria

### 1. DDD Package Structure

- [ ] All code organized into bounded contexts: actor, activity, social, federation, webfinger, shared
- [ ] No circular dependencies between contexts
- [ ] Each context has clear responsibilities

### 2. ActivityType Enum

- [ ] ActivityType enum defines: RUN, RIDE, SWIM, WALK, HIKE, NOTE
- [ ] Each type has display name and ActivityPub object type
- [ ] Enum can convert to/from ActivityPub type strings

### 3. JSONB Storage

- [ ] V2 migration adds `object_json` JSONB column to activities table
- [ ] GIN index on `object_json` for efficient queries
- [ ] Activity entity supports JSONB field with proper Hibernate mapping
- [ ] Custom activity objects stored and retrieved correctly

### 4. ActivityRepository

- [ ] Repository pattern for activity queries
- [ ] Find activities by actor
- [ ] Find activities by type
- [ ] Find activities by activityId (ActivityPub ID)
- [ ] Paginated queries

### 5. ActivityService

- [ ] Create activity with JSONB object storage
- [ ] Retrieve activity with proper object deserialization
- [ ] Support for Note objects (TEXT storage)
- [ ] Support for custom types (JSONB storage)

### 6. API Endpoints

- [ ] GET /api/activities — List activities with pagination
- [ ] GET /api/activities/{id} — Get activity by ID
- [ ] POST /api/activities — Create activity (UI-friendly)
- [ ] Responses use standard JSON (not ActivityPub format)

### 7. Tests

- [ ] Unit tests for ActivityType enum
- [ ] Unit tests for ActivityRepository
- [ ] Unit tests for ActivityService
- [ ] Integration tests for API endpoints
- [ ] All existing Sprint 1 tests still pass
