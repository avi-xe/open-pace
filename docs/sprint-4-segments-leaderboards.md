# Sprint 4: Segments & Leaderboards

## Metadata

- **Sprint Number**: 4
- **Estimated Time**: 4-5 hours
- **Complexity**: High
- **Dependencies**: Sprint 1 (ActivityPub), Sprint 2 (Custom Activity Types), Sprint 3 (GPX/Map)

## Implementation Goals

1. Define named segments (stretches of road/trail) with geographic boundaries
2. Record segment efforts when activities pass through segments
3. Build leaderboards ranking users by fastest times on segments
4. Provide overall leaderboards (most segments completed, most distance)

## Prerequisites Checklist

- [x] Completed Sprint 1: Basic ActivityPub Server
- [x] Completed Sprint 2: Custom Activity Types
- [x] Completed Sprint 3: GPX File Handling & Visualization
- [x] Track data stored as JSONB with lat/lon points

## What Gets Implemented

Users can create named segments (e.g., "Heartbreak Hill", "Alpe d'Huez") defined by a start/end point and a polyline. When an activity's GPX track overlaps a segment, the system records a segment effort with elapsed time. Leaderboards rank athletes by fastest time on each segment, and overall leaderboards show who has completed the most segments.

## Architecture Changes

### New Components

- **Segment**: Entity representing a named geographic segment
- **SegmentEffort**: Entity linking an activity to a segment with timing data
- **SegmentService**: Business logic for segment matching, effort recording, leaderboard queries
- **SegmentResource**: REST endpoints for segments, efforts, and leaderboards
- **Database migration V4**: Adds `segments` and `segment_efforts` tables

### Data Flow

```
Create Segment → SegmentService.createSegment()
                    ├── Validate start/end coordinates
                    ├── Store polyline (encoded or JSONB)
                    └── Persist segment

Activity Created → SegmentService.matchSegments()
                    ├── Load all segments
                    ├── For each segment, check if activity track overlaps
                    ├── Calculate elapsed time on segment
                    └── Create SegmentEffort records

Leaderboard Request → SegmentService.getLeaderboard()
                    ├── Query SegmentEfforts for segment
                    ├── Group by actor, take best time per actor
                    ├── Rank by fastest time
                    └── Return ranked list
```

### Package Structure Additions

```
org.openpace.segment/
├── Segment.java              # Segment entity
├── SegmentEffort.java        # Segment effort entity
├── SegmentRepository.java    # Database queries
├── SegmentService.java       # Business logic
└── SegmentResource.java      # REST endpoints
```

## Implementation Steps

### Step 1: Database Migration

**Goal**: Create segments and segment_efforts tables

**Migration SQL** (`V4__add_segments.sql`):

```sql
-- Segments: named stretches of road/trail
CREATE TABLE segments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    activity_type VARCHAR(50) NOT NULL,  -- Run, Ride, etc.
    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,
    end_lat DOUBLE PRECISION NOT NULL,
    end_lon DOUBLE PRECISION NOT NULL,
    distance DOUBLE PRECISION NOT NULL,  -- meters
    elevation_gain DOUBLE PRECISION DEFAULT 0,
    elevation_loss DOUBLE PRECISION DEFAULT 0,
    created_by BIGINT REFERENCES actors(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_segments_activity_type ON segments(activity_type);
CREATE INDEX idx_segments_start ON segments(start_lat, start_lon);

-- Segment efforts: an activity's completion of a segment
CREATE TABLE segment_efforts (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT REFERENCES segments(id) NOT NULL,
    activity_id BIGINT REFERENCES activities(id) NOT NULL,
    actor_id BIGINT REFERENCES actors(id) NOT NULL,
    elapsed_time BIGINT NOT NULL,  -- seconds
    started_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(segment_id, activity_id)  -- one effort per activity per segment
);

CREATE INDEX idx_segment_efforts_segment ON segment_efforts(segment_id);
CREATE INDEX idx_segment_efforts_actor ON segment_efforts(actor_id);
CREATE INDEX idx_segment_efforts_time ON segment_efforts(segment_id, elapsed_time);
```

### Step 2: Segment Entity

**Goal**: JPA entity for segments

```java
@Entity
@Table(name = "segments")
public class Segment extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @NotBlank
    @Column(nullable = false)
    public String name;

    public String description;

    @NotBlank
    @Column(name = "activity_type", nullable = false, length = 50)
    public String activityType;

    @Column(name = "start_lat", nullable = false)
    public double startLat;

    @Column(name = "start_lon", nullable = false)
    public double startLon;

    @Column(name = "end_lat", nullable = false)
    public double endLat;

    @Column(name = "end_lon", nullable = false)
    public double endLon;

    @Column(nullable = false)
    public double distance;

    @Column(name = "elevation_gain")
    public double elevationGain;

    @Column(name = "elevation_loss")
    public double elevationLoss;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    public Actor createdBy;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
```

### Step 3: SegmentEffort Entity

**Goal**: JPA entity linking activities to segments

```java
@Entity
@Table(name = "segment_efforts")
public class SegmentEffort extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false)
    public Segment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    public Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    public Actor actor;

    @Column(name = "elapsed_time", nullable = false)
    public long elapsedTime;  // seconds

    @Column(name = "started_at", nullable = false)
    public LocalDateTime startedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
```

### Step 4: SegmentService

**Goal**: Business logic for segments, efforts, and leaderboards

Key methods:
- `createSegment(...)` - Create a new segment
- `getSegment(Long id)` - Get segment by ID
- `listSegments(String activityType)` - List segments, optionally filtered
- `matchAndRecordEfforts(Activity activity)` - Match activity track to segments, record efforts
- `getLeaderboard(Long segmentId)` - Get ranked leaderboard for a segment
- `getOverallLeaderboard(String activityType)` - Overall stats across segments

### Step 5: SegmentResource

**Goal**: REST endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/segments` | Create a segment |
| GET | `/api/segments` | List segments (filterable by type) |
| GET | `/api/segments/{id}` | Get segment details |
| POST | `/api/segments/{id}/efforts` | Record an effort (auto-matched or manual) |
| GET | `/api/segments/{id}/leaderboard` | Get leaderboard for segment |
| GET | `/api/leaderboards/overall` | Overall leaderboard across segments |

### Step 6: Integration with Activity Creation

When a new activity with track data is created, automatically match it against known segments and record efforts. This hooks into `ActivityService.createActivity()`.

## Testing Your Implementation

### Manual Testing

```bash
# Start dev server
./mvnw quarkus:dev

# Create a segment
curl -X POST http://localhost:8080/api/segments \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Hill","activityType":"Run","startLat":42.3601,"startLon":-71.0589,"endLat":42.3610,"endLon":-71.0580,"distance":500}'

# List segments
curl http://localhost:8080/api/segments

# Get leaderboard
curl http://localhost:8080/api/segments/1/leaderboard
```

### Automated Tests

```bash
./mvnw test
```

## Error Handling

All endpoints use consistent error handling:
- **Validation**: Hibernate Validator annotations on request DTOs
- **Error Format**: `{"error": "<ERROR_CODE>", "message": "<MESSAGE>"}`
- **Status Codes**: 400 for validation, 404 for not found, 201 for creation

## Next Steps

- ✅ You've completed Sprint 4!
- 🎯 **Next**: Sprint 5: Privacy Controls & Data Export
