# Sprint 2 Design: Custom Activity Types

## Architecture

### DDD Bounded Contexts

```
org.openpace
  ├── actor/                    # Actor identity context
  │   ├── Actor.java           # Entity
  │   └── ActorResource.java   # ActivityPub endpoint
  │
  ├── activity/                 # Activity tracking context
  │   ├── Activity.java        # Entity
  │   ├── ActivityType.java    # Enum (NEW)
  │   ├── ActivityRepository.java # Repository (NEW)
  │   ├── ActivityService.java # Service (NEW)
  │   ├── ActivityResource.java # ActivityPub endpoint
  │   └── models/
  │       └── ActivityPubModels.java
  │
  ├── social/                   # Social relationships context
  │   ├── Follower.java        # Entity
  │   └── FollowersResource.java # ActivityPub endpoint
  │
  ├── federation/               # Federation delivery context
  │   ├── FederationDeliveryService.java
  │   ├── InboxResource.java   # ActivityPub endpoint
  │   └── OutboxResource.java  # ActivityPub endpoint
  │
  ├── webfinger/                # Discovery context
  │   └── WebFingerResource.java
  │
  └── shared/                   # Shared kernel
      ├── OpenPaceApplication.java
      ├── ErrorResponse.java
      └── exception/
```

### JSONB Storage Strategy

**Dual Storage**:

- `object_content` (TEXT) — For Note objects (simple text)
- `object_json` (JSONB) — For custom types (Run, Ride, etc.)

**Retrieval Logic**:

```java
if (activity.objectJson != null) {
    // Use stored JSON (custom type)
    return activity.objectJson;
} else {
    // Reconstruct Note from objectContent
    return reconstructNote(activity.objectContent);
}
```

### ActivityType Enum

```java
public enum ActivityType {
    NOTE("Note", "Note"),
    RUN("Run", "https://fedisports.example/ns#Run"),
    RIDE("Ride", "https://fedisports.example/ns#Ride"),
    SWIM("Swim", "https://fedisports.example/ns#Swim"),
    WALK("Walk", "https://fedisports.example/ns#Walk"),
    HIKE("Hike", "https://fedisports.example/ns#Hike");
    
    private final String displayName;
    private final String activityPubType;
}
```

### Database Migration (V2)

```sql
-- Add JSONB column for custom activity objects
ALTER TABLE activities 
ADD COLUMN object_json JSONB;

-- GIN index for JSONB queries
CREATE INDEX idx_activities_object_json 
ON activities USING GIN (object_json);
```

### API Response Format

```json
{
  "id": 123,
  "type": "Run",
  "name": "Morning 5K",
  "distance": 5000,
  "duration": "PT25M30S",
  "author": {
    "username": "alice",
    "name": "Alice Runner"
  },
  "publishedAt": "2026-01-17T07:00:00Z"
}
```

## Trade-offs

### JSONB vs Strongly-Typed Columns

**JSONB (chosen)**:

- ✅ Flexible schema for different activity types
- ✅ No schema changes for new types
- ✅ Preserves all properties exactly
- ❌ Less type safety
- ❌ Requires careful handling

**Strongly-Typed Columns**:

- ✅ Type safety
- ✅ Better query performance
- ❌ Schema changes for new types
- ❌ Many nullable columns

**Decision**: JSONB for flexibility. We can add typed columns later for frequently queried fields.

### Repository Pattern vs Direct Panache

**Repository (chosen)**:

- ✅ Clean separation of query logic
- ✅ Testable in isolation
- ✅ Consistent query patterns
- ❌ More classes

**Direct Panache**:

- ✅ Simpler
- ❌ Query logic scattered

**Decision**: Repository for clean architecture.

## Dependencies

### Existing (Sprint 1)

- Quarkus 3.30.6
- Hibernate Panache
- PostgreSQL
- Flyway

### New (Sprint 2)

- Jackson JsonNode (already included)
- No new dependencies needed

## Risk Assessment

- **Low Risk**: DDD restructure is mechanical (move files, update packages)
- **Low Risk**: JSONB is well-supported in PostgreSQL + Hibernate
- **Medium Risk**: ActivityPub models may need adjustment for custom types
- **Mitigation**: Keep Sprint 1 behavior working throughout restructure
