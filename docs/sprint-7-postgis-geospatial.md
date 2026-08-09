# Sprint 7: PostGIS Geospatial Storage

## Metadata
- **Sprint Number**: 7
- **Estimated Time**: 3-4 hours
- **Complexity**: High
- **Dependencies**: Sprint 3 (GPX Visualization), Sprint 4 (Segments)

## Implementation Goals

1. Enable PostGIS extension in PostgreSQL
2. Store simplified tracks as PostGIS LineString geometry
3. Store start/end points as PostGIS Point geometry
4. Add spatial indexes for efficient geospatial queries
5. Add spatial query endpoints (nearby activities, bounding box)
6. Update Activity entity with geometry fields
7. Populate geometry data when activities with GPX are created

## Prerequisites

- [x] Sprint 3: GPX parsing and track data storage
- [x] Sprint 4: Segments with location data
- [x] PostgreSQL with PostGIS available (Dev Services)

## Architecture Changes

### New Dependencies

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-spatial</artifactId>
</dependency>
```

### Database Migration (V8)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE activities ADD COLUMN track_line geometry(LineString, 4326);
ALTER TABLE activities ADD COLUMN start_point geometry(Point, 4326);
ALTER TABLE activities ADD COLUMN end_point geometry(Point, 4326);

CREATE INDEX idx_activities_track_line ON activities USING GIST (track_line);
CREATE INDEX idx_activities_start_point ON activities USING GIST (start_point);
CREATE INDEX idx_activities_end_point ON activities USING GIST (end_point);
```

### Entity Changes

Activity entity gains three geometry fields:
- `trackLine` — simplified GPX track as LineString
- `startPoint` — first coordinate as Point
- `endPoint` — last coordinate as Point

### Spatial Queries

| Endpoint | Purpose |
|----------|---------|
| `GET /api/activities/nearby?lat=&lon=&radius=` | Activities within radius (meters) |
| `GET /api/activities/bounding-box?minLat=&minLon=&maxLat=&maxLon=` | Activities in bounding box |

## Implementation Steps

1. [ ] Add hibernate-spatial dependency to pom.xml
2. [ ] Create V8__add_postgis.sql migration
3. [ ] Configure PostGIS dialect in application.properties
4. [ ] Update Activity entity with geometry fields
5. [ ] Update ActivityService to populate geometry from GPX data
6. [ ] Add spatial query methods to ActivityRepository
7. [ ] Add nearby/bounding-box endpoints to ActivityResource
8. [ ] Add tests for spatial queries
9. [ ] Verify all existing tests still pass
10. [ ] Test with real GPX data in dev mode

## Technical Details

### PostGIS Dialect Configuration

```properties
# application.properties
quarkus.hibernate-orm.dialect=org.hibernate.spatial.dialect.postgis.PostgisDialect
```

### Geometry Population Logic

When an activity with GPX data is created:
1. Parse track points from `trackData` JSONB
2. Build LineString from coordinate array
3. Extract first/last points for start/end
4. Store as PostGIS geometry with SRID 4326

### Simplified Track Generation

For large GPX files (>1000 points), apply Douglas-Peucker simplification:
- Tolerance: 0.0001 degrees (~11 meters)
- Preserves track shape while reducing storage
- Essential for performant spatial queries

## Verification

1. `./mvnw test` — all tests pass
2. Create activity with GPX via outbox → geometry columns populated
3. Query nearby activities → returns results within radius
4. Query bounding box → returns activities in area
5. Map image generation still works (uses existing track_data JSONB)
