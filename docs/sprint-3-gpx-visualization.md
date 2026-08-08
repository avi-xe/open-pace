# Sprint 3: GPX File Handling & Activity Visualization

## Metadata

- **Sprint Number**: 3
- **Estimated Time**: 4-5 hours
- **Complexity**: High
- **Dependencies**: Sprint 1 (ActivityPub), Sprint 2 (Custom Activity Types)

## Implementation Goals

1. Parse GPX files to extract track points (lat/lon, elevation, timestamps)
2. Store parsed track data alongside activities
3. Generate static map images from track data using OSM tiles
4. Serve elevation profile data for activities
5. Export activities as GPX files

## Prerequisites Checklist

- [x] Completed Sprint 1: Basic ActivityPub Server
- [x] Completed Sprint 2: Custom Activity Types (Run, Ride, Swim, Walk, Hike)
- [x] Understanding of GPX XML structure
- [x] Understanding of OSM tile system

## What Gets Implemented

Users can upload GPX files with their activities. The system parses track points, extracts distance/duration/pace, and stores the data. Activities get static map images showing the route on OpenStreetMap tiles, elevation profile data for charting, and the ability to download the activity as a GPX file.

## Architecture Changes

### New Components

- **GpxService**: Parses GPX XML, extracts track points, calculates distance/pace/elevation
- **MapImageService**: Fetches OSM tiles, composites base map, draws track overlay
- **MapResource**: REST endpoints for map images, elevation profiles, and GPX export
- **Database migration V3**: Adds `gpx_data` (raw GPX) and `track_data` (parsed JSONB) columns

### Data Flow

```
GPX Upload → GpxService.parseGpx()
                ├── Extract track points (lat, lon, ele, time)
                ├── Calculate distance, duration, pace
                ├── Calculate elevation gain/loss
                └── Return GpxData object
                     ↓
            ActivityService.createActivity()
                ├── Store raw GPX in gpx_data (TEXT)
                ├── Store parsed points in track_data (JSONB)
                └── Persist activity
                     ↓
            MapImageService.generateMap()
                ├── Fetch OSM tiles for bounding box
                ├── Composite tiles into base map
                ├── Draw track polyline
                ├── Draw start/end markers
                └── Return PNG image
```

### Package Structure Additions

```
org.openpace.activity
├── GpxService.java              # GPX parsing + data extraction
├── GpxData.java                 # Parsed GPX data model (POJO)
├── TrackPoint.java              # Individual track point (lat, lon, ele, time, speed)
├── MapImageService.java         # OSM tile fetching + image generation
├── MapResource.java             # REST endpoints for maps, elevation, GPX export
└── [existing files]
```

## Implementation Steps

### Step 1: Add Dependencies

**Time**: 10 minutes
**Goal**: Add GPX parsing and geometry libraries

**Code Changes**: `pom.xml`

**Implementation**:

```xml
<!-- GPX file parsing -->
<dependency>
    <groupId>io.jenetics</groupId>
    <artifactId>jpx</artifactId>
    <version>3.0.1</version>
</dependency>

<!-- Geometry types for track calculations -->
<dependency>
    <groupId>org.locationtech.jts</groupId>
    <artifactId>jts-core</artifactId>
    <version>1.18.2</version>
</dependency>
```

**Test**: `./mvnw compile` succeeds with new dependencies

---

### Step 2: Database Migration

**Time**: 15 minutes
**Goal**: Add columns for GPX data storage

**Code Changes**: `V3__add_gpx_data.sql`, `Activity.java`

**Migration SQL**:

```sql
-- Store raw GPX XML for re-export and re-parsing
ALTER TABLE activities ADD COLUMN gpx_data TEXT;

-- Store parsed track data as JSONB for fast queries
-- Structure: { "points": [...], "summary": { distance, duration, pace, elevationGain, elevationLoss } }
ALTER TABLE activities ADD COLUMN track_data JSONB;

-- GIN index for querying track data properties
CREATE INDEX idx_activities_track_data ON activities USING GIN (track_data);
```

**Entity Changes** (`Activity.java`):

```java
/**
 * Raw GPX XML data, stored for re-export and re-parsing.
 */
@Column(name = "gpx_data", columnDefinition = "TEXT")
public String gpxData;

/**
 * Parsed track data as JSONB.
 * Structure: { "points": [{lat, lon, ele, time, speed}], "summary": {...} }
 */
@Column(name = "track_data", columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
public com.fasterxml.jackson.databind.JsonNode trackData;
```

**Test**: Application starts, migration runs successfully

---

### Step 3: GPX Parsing Service

**Time**: 1 hour
**Goal**: Parse GPX files and extract structured track data

**Code Changes**: `GpxService.java`, `GpxData.java`, `TrackPoint.java`

**Data Models**:

```java
/**
 * Represents a single point in a GPX track.
 */
public class TrackPoint {
    public double latitude;
    public double longitude;
    public double elevation;    // meters
    public Instant timestamp;
    public double speed;        // m/s (calculated from timestamps)
}

/**
 * Summary statistics computed from track points.
 */
public class TrackSummary {
    public double totalDistance;      // meters
    public long totalDuration;       // seconds
    public double averagePace;       // seconds per km (running) or seconds per km (cycling)
    public double elevationGain;     // meters
    public double elevationLoss;     // meters
    public double maxSpeed;          // m/s
    public double averageSpeed;      // m/s
}

/**
 * Complete parsed GPX data.
 */
public class GpxData {
    public List<TrackPoint> points;
    public TrackSummary summary;
    public double minLat;
    public double maxLat;
    public double minLon;
    public double maxLon;
}
```

**Service Implementation**:

```java
@ApplicationScoped
public class GpxService {

    private static final double EARTH_RADIUS = 6371000; // meters

    /**
     * Parse GPX XML string and extract track data.
     * Returns null if no track data found.
     */
    public GpxData parseGpx(String gpxXml) {
        // Parse XML using jpx library
        // Extract all <trkpt> elements
        // Convert to TrackPoint list
        // Calculate speed between points
        // Compute summary statistics
        // Return GpxData with points and summary
    }

    /**
     * Calculate distance between two coordinates using Haversine formula.
     */
    public double calculateDistance(double lat1, double lon1, 
                                     double lat2, double lon2) {
        // Haversine formula implementation
        // Returns distance in meters
    }

    /**
     * Calculate elevation gain and loss from track points.
     */
    public double[] calculateElevationChanges(List<TrackPoint> points) {
        // Sum positive changes (gain) and negative changes (loss)
        // Return [gain, loss] in meters
    }

    /**
     * Calculate average pace from distance and duration.
     * Pace is seconds per kilometer.
     */
    public double calculatePace(double distanceMeters, long durationSeconds) {
        if (distanceMeters <= 0) return 0;
        return (durationSeconds / distanceMeters) * 1000;
    }
}
```

**Explanation**: GPX files contain XML with `<trk>` → `<trkseg>` → `<trkpt>` elements. Each track point has latitude, longitude, optional elevation (`<ele>`), and optional timestamp (`<time>`). We extract these, calculate speed from consecutive timestamps and distance, and compute summary statistics.

**Test**: Parse sample GPX file, verify point extraction and distance calculations

---

### Step 4: Map Image Service

**Time**: 1.5 hours
**Goal**: Generate static map images with track overlay

**Code Changes**: `MapImageService.java`

**OSM Tile System**:
- Tiles are 256x256 PNG images
- URL pattern: `https://tile.openstreetmap.org/{z}/{x}/{y}.png`
- Zoom level 0 = single tile covering entire world
- Each zoom level doubles tile count in each dimension

**Implementation**:

```java
@ApplicationScoped
public class MapImageService {

    private static final String OSM_TILE_URL = 
        "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final int TILE_SIZE = 256;
    private static final int MAP_WIDTH = 800;
    private static final int MAP_HEIGHT = 600;

    // Vert.x WebClient for non-blocking HTTP requests
    @Inject
    io.vertx.mutiny.web.client.WebClient webClient;

    /**
     * Generate a static map image showing the track.
     * Returns PNG bytes.
     */
    public byte[] generateMapImage(GpxData gpxData) {
        // 1. Calculate bounding box from track points
        // 2. Determine zoom level based on bounding box size
        // 3. Calculate tile coordinates needed
        // 4. Fetch tiles from OSM (in parallel using Vert.x)
        // 5. Composite tiles into base map image
        // 6. Draw track polyline on base map
        // 7. Draw start marker (green) and end marker (red)
        // 8. Add attribution text (required by OSM)
        // 9. Convert to PNG bytes
        // 10. Return image bytes
    }

    /**
     * Convert lat/lon to pixel position on the composite map.
     */
    private Point2D latLonToPixel(double lat, double lon, 
                                    int zoom, int offsetX, int offsetY) {
        // Convert lat/lon to tile coordinates
        // Convert fractional tile to pixel offset
        // Adjust for composite map origin
        // Return pixel position
    }

    /**
     * Draw track polyline on the map image.
     */
    private void drawTrack(Graphics2D g, GpxData gpxData, 
                           int zoom, int offsetX, int offsetY) {
        // Set stroke width and color (blue)
        // Convert each track point to pixel coordinates
        // Draw connected line segments
    }

    /**
     * Draw a marker at a specific location.
     * color: Color for marker (GREEN for start, RED for end)
     */
    private void drawMarker(Graphics2D g, double lat, double lon,
                           Color color, int zoom, 
                           int offsetX, int offsetY) {
        // Convert lat/lon to pixel
        // Draw filled circle with white border
    }

    /**
     * Add OSM attribution text to bottom-right corner.
     * Required by OSM Tile Usage Policy.
     */
    private void drawAttribution(Graphics2D g, int width, int height) {
        String attribution = "© OpenStreetMap contributors";
        // Draw text with white background for readability
    }
}
```

**Explanation**: We fetch OSM tiles covering the track's bounding box, composite them into a single image, then overlay the track as a polyline with start/end markers. The Vert.x WebClient handles non-blocking HTTP requests to fetch tiles in parallel. Attribution is required by OSM's tile usage policy.

**Performance Note**: Tile fetching is I/O-bound. Using Vert.x WebClient keeps the event loop unblocked. For high-volume usage, tiles should be cached (deferred to a future sprint).

**Test**: Generate map image from sample GPX data, verify output is valid PNG

---

### Step 5: REST Endpoints

**Time**: 1 hour
**Goal**: Expose map images, elevation data, and GPX export

**Code Changes**: `MapResource.java`

**Endpoints**:

```java
@Path("/api/activities/{activityId}")
@ApplicationScoped
public class MapResource {

    @Inject
    ActivityService activityService;

    @Inject
    MapImageService mapImageService;

    @Inject
    GpxService gpxService;

    /**
     * Get static map image for an activity.
     * Returns PNG image with track overlaid on OSM tiles.
     */
    @GET
    @Path("/map.png")
    @Produces("image/png")
    public Response getMapImage(@PathParam("activityId") Long activityId) {
        Activity activity = activityService.getActivity(activityId);
        if (activity == null || activity.trackData == null) {
            return Response.status(404).build();
        }

        GpxData gpxData = parseTrackData(activity.trackData);
        byte[] imageBytes = mapImageService.generateMapImage(gpxData);

        return Response.ok(imageBytes)
            .header("Content-Type", "image/png")
            .header("Cache-Control", "public, max-age=86400")
            .build();
    }

    /**
     * Get elevation profile data for charting.
     * Returns JSON array of {distance, elevation} points.
     */
    @GET
    @Path("/elevation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getElevationProfile(
            @PathParam("activityId") Long activityId) {
        Activity activity = activityService.getActivity(activityId);
        if (activity == null || activity.trackData == null) {
            return Response.status(404).build();
        }

        GpxData gpxData = parseTrackData(activity.trackData);

        // Build elevation profile: cumulative distance vs elevation
        List<Map<String, Double>> profile = new ArrayList<>();
        double cumulativeDistance = 0;

        for (int i = 0; i < gpxData.points.size(); i++) {
            TrackPoint point = gpxData.points.get(i);
            if (i > 0) {
                TrackPoint prev = gpxData.points.get(i - 1);
                cumulativeDistance += gpxService.calculateDistance(
                    prev.latitude, prev.longitude,
                    point.latitude, point.longitude
                );
            }

            Map<String, Double> entry = new HashMap<>();
            entry.put("distance", cumulativeDistance);  // meters
            entry.put("elevation", point.elevation);     // meters
            profile.add(entry);
        }

        return Response.ok(profile).build();
    }

    /**
     * Export activity as GPX file.
     * Returns GPX XML with original or reconstructed track data.
     */
    @GET
    @Path("/export.gpx")
    @Produces("application/gpx+xml")
    public Response exportGpx(
            @PathParam("activityId") Long activityId) {
        Activity activity = activityService.getActivity(activityId);
        if (activity == null) {
            return Response.status(404).build();
        }

        String gpxXml;
        if (activity.gpxData != null) {
            // Return original GPX if uploaded
            gpxXml = activity.gpxData;
        } else {
            // Generate GPX from stored track data
            gpxXml = gpxService.generateGpx(
                activity.getActivityName(),
                parseTrackData(activity.trackData)
            );
        }

        return Response.ok(gpxXml)
            .header("Content-Type", "application/gpx+xml")
            .header("Content-Disposition", 
                "attachment; filename=\"activity-" + activityId + ".gpx\"")
            .build();
    }

    /**
     * Upload GPX file for an existing activity.
     * Parses the GPX and stores track data.
     */
    @POST
    @Path("/gpx")
    @Consumes("application/gpx+xml")
    @Transactional
    public Response uploadGpx(
            @PathParam("activityId") Long activityId,
            String gpxXml) {
        Activity activity = activityService.getActivity(activityId);
        if (activity == null) {
            return Response.status(404).build();
        }

        // Parse GPX
        GpxData gpxData = gpxService.parseGpx(gpxXml);
        if (gpxData == null) {
            return Response.status(400)
                .entity("{\"error\": \"INVALID_GPX\", \"message\": \"No track data found in GPX\"}")
                .build();
        }

        // Store raw GPX and parsed data
        activity.gpxData = gpxXml;
        activity.trackData = convertToJsonNode(gpxData);
        activity.persist();

        return Response.ok(activity).build();
    }
}
```

**Integration with Activity Creation** (`ActivityService.java`):

```java
/**
 * Modified createActivity to accept optional GPX data.
 * If gpxData is provided, parse and store track data.
 */
public Activity createActivity(Actor actor, JsonNode activityObject) {
    Activity activity = new Activity();
    activity.actor = actor;
    // ... existing field extraction ...

    // Handle GPX data if present
    JsonNode gpxNode = activityObject.get("gpxData");
    if (gpxNode != null && gpxNode.isTextual()) {
        String gpxXml = gpxNode.asText();
        GpxData gpxData = gpxService.parseGpx(gpxXml);

        if (gpxData != null) {
            activity.gpxData = gpxXml;
            activity.trackData = convertToJsonNode(gpxData);

            // Auto-populate distance and duration from GPX
            if (activity.objectJson != null) {
                ((ObjectNode) activity.objectJson)
                    .put("distance", gpxData.summary.totalDistance)
                    .put("duration", gpxData.summary.totalDuration)
                    .put("averagePace", gpxData.summary.averagePace);
            }
        }
    }

    activity.persist();
    return activity;
}
```

**Test**: All endpoints return correct status codes and content types

---

### Step 6: Tests

**Time**: 1 hour
**Goal**: Unit tests for GPX parsing, service tests for map generation, integration tests for endpoints

**Code Changes**: Test files

**GPX Service Tests** (`GpxServiceTest.java`):

```java
@QuarkusTest
@TestTransaction
public class GpxServiceTest {

    @Inject
    GpxService gpxService;

    private static final String SAMPLE_GPX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1">
          <trk>
            <trkseg>
              <trkpt lat="47.365590" lon="8.524997">
                <ele>408</ele>
                <time>2026-08-01T08:00:00Z</time>
              </trkpt>
              <trkpt lat="47.365800" lon="8.525200">
                <ele>412</ele>
                <time>2026-08-01T08:01:00Z</time>
              </trkpt>
              <trkpt lat="47.366000" lon="8.525500">
                <ele>415</ele>
                <time>2026-08-01T08:02:00Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
        """;

    @Test
    void shouldParseGpxTrackPoints() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result);
        assertEquals(3, result.points.size());
        assertEquals(47.365590, result.points.get(0).latitude, 0.0001);
        assertEquals(8.524997, result.points.get(0).longitude, 0.0001);
    }

    @Test
    void shouldCalculateElevationChanges() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result.summary);
        assertEquals(7.0, result.summary.elevationGain, 0.1);  // 408→415
        assertEquals(0.0, result.summary.elevationLoss, 0.1);  // no descent
    }

    @Test
    void shouldCalculateDistance() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        // Distance between Zurich points should be ~50-100m
        assertTrue(result.summary.totalDistance > 30);
        assertTrue(result.summary.totalDistance < 200);
    }

    @Test
    void shouldCalculateSpeed() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        // Speed from timestamps: 2 minutes for ~50m = ~0.4 m/s
        assertNotNull(result.summary.averageSpeed);
        assertTrue(result.summary.averageSpeed > 0);
    }

    @Test
    void shouldHandleEmptyGpx() {
        String emptyGpx = """
            <?xml version="1.0"?>
            <gpx version="1.1"></gpx>
            """;

        GpxData result = gpxService.parseGpx(emptyGpx);
        assertNull(result);
    }

    @Test
    void shouldCalculateBoundingBox() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertEquals(47.365590, result.minLat, 0.0001);
        assertEquals(47.366000, result.maxLat, 0.0001);
        assertEquals(8.524997, result.minLon, 0.0001);
        assertEquals(8.525500, result.maxLon, 0.0001);
    }
}
```

**Map Resource Tests** (`MapResourceTest.java`):

```java
@QuarkusTest
public class MapResourceTest {

    @Test
    void shouldReturnMapImage() {
        given()
            .pathParam("id", 1)
        .when()
            .get("/api/activities/{id}/map.png")
        .then()
            .statusCode(200)
            .header("Content-Type", "image/png");
    }

    @Test
    void shouldReturnElevationProfile() {
        given()
            .pathParam("id", 1)
        .when()
            .get("/api/activities/{id}/elevation")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("size()", greaterThan(0))
            .body("[0].distance", notNullValue())
            .body("[0].elevation", notNullValue());
    }

    @Test
    void shouldExportGpx() {
        given()
            .pathParam("id", 1)
        .when()
            .get("/api/activities/{id}/export.gpx")
        .then()
            .statusCode(200)
            .header("Content-Type", "application/gpx+xml")
            .header("Content-Disposition", containsString("activity-1.gpx"));
    }

    @Test
    void shouldReturn404ForMissingActivity() {
        given()
            .pathParam("id", 99999)
        .when()
            .get("/api/activities/{id}/map.png")
        .then()
            .statusCode(404);
    }
}
```

**Manual Testing**:

1. Start the server:
   ```bash
   ./mvnw quarkus:dev
   ```

2. Create activity with GPX:
   ```bash
   curl -X POST http://localhost:8080/users/alice/outbox \
     -H "Content-Type: application/activity+json" \
     -d '{
       "type": "Create",
       "object": {
         "type": "https://fedisports.example/ns#Run",
         "name": "Morning Run",
         "gpxData": "<base64-encoded-gpx>"
       }
     }'
   ```

3. Get map image:
   ```bash
   curl -o map.png http://localhost:8080/api/activities/1/map.png
   open map.png
   ```

4. Get elevation profile:
   ```bash
   curl http://localhost:8080/api/activities/1/elevation
   ```

5. Export as GPX:
   ```bash
   curl -o activity.gpx http://localhost:8080/api/activities/1/export.gpx
   ```

## Code Structure

### New Packages

```
org.openpace.activity
├── GpxService.java              # GPX parsing and data extraction
├── GpxData.java                 # Parsed GPX data model
├── TrackPoint.java              # Individual track point
├── TrackSummary.java            # Computed statistics
├── MapImageService.java         # OSM tile fetching and image generation
├── MapResource.java             # REST endpoints for maps, elevation, export
└── [existing: Activity.java, ActivityService.java, etc.]
```

### Modified Files

- `pom.xml`: Add jpx and jts-core dependencies
- `Activity.java`: Add gpxData and trackData fields
- `ActivityService.java`: Integrate GPX parsing on activity creation

### Configuration Changes

- `application.properties`: No changes needed (OSM tiles are public)

## Testing Strategy

### Unit Tests

- [x] GPX parsing (valid GPX, empty GPX, malformed XML)
- [x] Distance calculation (Haversine formula)
- [x] Elevation gain/loss calculation
- [x] Speed calculation from timestamps
- [x] Bounding box calculation

### Integration Tests

- [x] Map image generation endpoint
- [x] Elevation profile endpoint
- [x] GPX export endpoint
- [x] GPX upload endpoint
- [x] 404 for missing activities

### Manual Tests

- [ ] Upload GPX via curl, verify map image is valid PNG
- [ ] View map image in browser, confirm track is visible
- [ ] Check elevation profile JSON has correct structure
- [ ] Download GPX export, validate XML structure
- [ ] Test with real GPX file from Garmin/watch

## Key Concepts to Document

### GPX File Format

- **What**: XML-based format for GPS exchange data
- **Why**: Universal format exported by all GPS devices and fitness apps
- **How**: Parse XML with jpx library, extract `<trkpt>` elements
- **Example**:
  ```xml
  <gpx>
    <trk>
      <trkseg>
        <trkpt lat="47.365590" lon="8.524997">
          <ele>408</ele>
          <time>2026-08-01T08:00:00Z</time>
        </trkpt>
      </trkseg>
    </trk>
  </gpx>
  ```

### OSM Tile System

- **What**: Web Map Tile Service used by OpenStreetMap
- **Why**: Free, open-source map tiles with proper attribution
- **How**: Fetch tiles at `{z}/{x}/{y}.png`, composite into map image
- **Example**: `https://tile.openstreetmap.org/12/2200/1343.png`

### Haversine Formula

- **What**: Calculate great-circle distance between two lat/lon points
- **Why**: Accurate distance calculation on Earth's surface
- **How**: Uses spherical trigonometry to compute distance in meters
- **Example**: Distance between Zurich (47.365, 8.525) and Bern (46.948, 7.447) ≈ 125 km

### Elevation Profile

- **What**: Chart data showing elevation changes over distance
- **Why**: Visualize terrain difficulty and effort
- **How**: Return JSON array of {distance, elevation} points for client-side charting
- **Example**: `[{"distance": 0, "elevation": 408}, {"distance": 1000, "elevation": 450}]`

## Common Pitfalls

### Pitfall 1: OSM Tile Rate Limiting

- **Why it happens**: Fetching too many tiles too quickly
- **How to avoid**: Limit concurrent tile requests, implement caching
- **How to fix**: Add delay between requests, use tile cache
- **Future**: Implement disk-based tile cache (deferred)

### Pitfall 2: Large GPX Files

- **Why it happens**: Long activities can have 10,000+ track points
- **How to avoid**: Store full data, but paginate for visualization
- **How to fix**: Client-side downsampling for display, server returns all points
- **Future**: Track simplification with Douglas-Peucker (deferred)

### Pitfall 3: Missing Timestamps

- **Why it happens**: Some GPX files don't include timestamps
- **How to avoid**: Calculate speed from distance only if timestamps missing
- **How to fix**: Set speed to 0 or estimate from average pace
- **Prevention**: Document that timestamps are preferred for accurate speed

### Pitfall 4: Timezone Handling

- **Why it happens**: GPX timestamps may be UTC or local time
- **How to avoid**: Always store as UTC Instant
- **How to fix**: Parse ISO 8601 format, convert to UTC
- **Prevention**: Use `Instant.parse()` which handles timezone correctly

## Performance Considerations

### Tile Fetching

- **Current**: Fetch tiles on each request (no caching)
- **Impact**: Slow first request for each map
- **Future**: Implement disk-based tile cache (deferred to future sprint)

### Image Generation

- **Current**: Generate PNG on each request
- **Impact**: CPU-intensive for complex tracks
- **Future**: Cache generated images (deferred)

### GPX Parsing

- **Current**: Parse XML synchronously
- **Impact**: Blocking for large GPX files
- **Future**: Async parsing with Vert.x (acceptable for MVP)

## Implementation Decisions

### Storage Decision

**Choice**: Store both raw GPX (TEXT) and parsed track data (JSONB)

**Rationale**:
- Raw GPX needed for re-export and re-parsing if format changes
- Parsed JSONB needed for fast queries and visualization
- Trade-off: Doubles storage, but simplifies implementation

### No Track Simplification (Deferred)

**Choice**: Store all track points without simplification

**Rationale**:
- Simplification adds complexity (Douglas-Peucker algorithm)
- For MVP, full resolution is acceptable
- Can add later for performance optimization

### No Image Caching (Deferred)

**Choice**: Generate map images on each request

**Rationale**:
- Caching adds infrastructure complexity (disk/Redis)
- For low-volume usage, regeneration is acceptable
- Can add later when caching strategy is defined

## Transition to Next Sprint

- **What's missing**: Track simplification, image caching, PostGIS storage
- **Teaser**: Sprint 4 adds segments and leaderboards, using track data for segment matching

## Review Checklist

1. [ ] GPX parsing extracts all track points correctly
2. [ ] Distance calculation matches expected values
3. [ ] Elevation gain/loss is accurate
4. [ ] Speed calculation uses timestamps correctly
5. [ ] Map image shows track on OSM tiles
6. [ ] Start and end markers are visible
7. [ ] Attribution text is present on map
8. [ ] Elevation profile returns correct JSON structure
9. [ ] GPX export produces valid XML
10. [ ] Activity creation accepts GPX data
11. [ ] All endpoints return correct status codes
12. [ ] All tests pass