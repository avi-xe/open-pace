# Frequently Asked Questions

## Alice wants to create a new Open Pace account, load an activity, and view the related map

This walkthrough covers the end-to-end user journey: signing up, posting a fitness activity with GPS data, and viewing the route on a map.

---

### Step 1: Create an Account

**Current state:** Open Pace does not yet expose a public registration endpoint. Actors (accounts) are created at the data layer. In production, a `POST /api/register` endpoint or admin provisioning workflow is planned (see `docs/SECURITY_INTEGRATION.md`).

For development and testing, Alice's actor record is inserted directly:

```sql
INSERT INTO actors (username, name) VALUES ('alice', 'Alice Runner');
```

Or programmatically via Panache:

```java
Actor alice = new Actor("alice", "Alice Runner");
alice.persist();
```

**Username rules** (enforced by `Actor.java`):
- 3–100 characters
- Lowercase letters, digits, underscores, and hyphens only (`^[a-z0-9_-]+$`)
- Must be unique

Once the actor exists, Alice is reachable at:
- **Actor URL:** `https://open-pace.example.com/users/alice`
- **Outbox:** `https://open-pace.example.com/users/alice/outbox`
- **Inbox:** `https://open-pace.example.com/users/alice/inbox`
- **Followers:** `https://open-pace.example.com/users/alice/followers`

---

### Step 2: Load an Activity

Alice posts a fitness activity to her outbox using the ActivityPub C2S (Client-to-Server) pattern.

**Request:**

```http
POST /users/alice/outbox
Content-Type: application/activity+json

{
  "type": "Create",
  "object": {
    "type": "Note",
    "name": "Morning Run in Central Park",
    "content": "Great 5K loop around the reservoir!",
    "gpxData": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\">\n  <trk>\n    <name>Morning Run</name>\n    <trkseg>\n      <trkpt lat=\"40.7812\" lon=\"-73.9665\"><ele>42</ele><time>2025-01-15T07:30:00Z</time></trkpt>\n      <trkpt lat=\"40.7829\" lon=\"-73.9654\"><ele>45</ele><time>2025-01-15T07:32:00Z</time></trkpt>\n      <trkpt lat=\"40.7845\" lon=\"-73.9630\"><ele>48</ele><time>2025-01-15T07:34:00Z</time></trkpt>\n      <trkpt lat=\"40.7830\" lon=\"-73.9610\"><ele>44</ele><time>2025-01-15T07:36:00Z</time></trkpt>\n      <trkpt lat=\"40.7812\" lon=\"-73.9665\"><ele>42</ele><time>2025-01-15T07:38:00Z</time></trkpt>\n    </trkseg>\n  </trk>\n</gpx>"
  }
}
```

**Response:**

```http
HTTP/1.1 201 Created
Location: https://open-pace.example.com/users/alice/activities/1705312200000

{
  "id": "https://open-pace.example.com/users/alice/activities/1705312200000"
}
```

**What happens server-side** (`ActivityService.createActivity`):

1. The `Create` activity is persisted with a unique `activityId` (`{actorUrl}/activities/{timestamp}`)
2. The GPX XML is parsed by `GpxService` — track points (lat, lon, elevation, time) are extracted
3. A **track summary** is calculated: total distance, duration, average pace, elevation gain/loss
4. Both the raw GPX and parsed track data are stored (`gpxData` column + `trackData` JSONB)
5. Distance, duration, and pace are auto-populated on the activity object
6. The activity is delivered to all of Alice's followers' inboxes via `FederationDeliveryService`

---

### Step 3: View the Activity

Alice (or anyone) can retrieve the activity in two ways:

**ActivityPub representation** (for federation):

```http
GET /activities/https://open-pace.example.com/users/alice/activities/1705312200000
Accept: application/activity+json
```

Returns a standard ActivityPub `Create` activity with the `Note` object.

**Outbox listing:**

```http
GET /users/alice/outbox
Accept: application/activity+json
```

Returns an `OrderedCollection` with activity IDs ordered by publish date (newest first).

---

### Step 4: View the Map

The map is generated on-the-fly from the stored track data.

**Static map image:**

```http
GET /api/activities/1/map.png
```

Returns an **800x600 PNG** with:
- OpenStreetMap tiles as the base layer
- Alice's track drawn as a polyline overlay
- Start marker (green) and end marker (red)
- OSM attribution in the corner

The image is cached (`Cache-Control: public, max-age=86400`).

**How it works** (`MapImageService.generateMapImage`):

1. The `trackData` JSONB is deserialized into a `GpxData` object
2. A bounding box is computed from the track's min/max lat/lon
3. The appropriate zoom level is chosen to fit the track
4. Required OSM tiles are fetched and composited into a base map
5. The track is drawn as a polyline with anti-aliasing
6. Start/end markers are placed at the first and last track points
7. The composited image is scaled to 800x600

**Elevation profile:**

```http
GET /api/activities/1/elevation
```

Returns a JSON array of `{distance, elevation}` points suitable for charting:

```json
[
  {"distance": 0.0,    "elevation": 42.0},
  {"distance": 234.5,  "elevation": 45.0},
  {"distance": 489.2,  "elevation": 48.0},
  {"distance": 720.1,  "elevation": 44.0},
  {"distance": 987.6,  "elevation": 42.0}
]
```

**GPX export:**

```http
GET /api/activities/1/export.gpx
```

Returns a downloadable `.gpx` file with the original or reconstructed track data.

---

### API Summary

| Step | Endpoint | Method | Content-Type |
|------|----------|--------|--------------|
| Create actor | *(data layer — no public endpoint yet)* | — | — |
| Post activity | `/users/{username}/outbox` | `POST` | `application/activity+json` |
| View activity | `/activities/{activityId}` | `GET` | `application/activity+json` |
| List outbox | `/users/{username}/outbox` | `GET` | `application/activity+json` |
| Map image | `/api/activities/{id}/map.png` | `GET` | `image/png` |
| Elevation data | `/api/activities/{id}/elevation` | `GET` | `application/json` |
| GPX export | `/api/activities/{id}/export.gpx` | `GET` | `application/gpx+xml` |
| GPX upload | `/api/activities/{id}/gpx` | `POST` | `application/gpx+xml` |

---

### Key Code Paths

| Component | File | Role |
|-----------|------|------|
| Actor entity | `src/main/java/org/openpace/actor/Actor.java` | Account model, username validation |
| Actor endpoint | `src/main/java/org/openpace/actor/ActorResource.java` | `GET /users/{username}` |
| Activity entity | `src/main/java/org/openpace/activity/Activity.java` | Activity model with dual storage |
| Activity service | `src/main/java/org/openpace/activity/ActivityService.java` | Activity creation, GPX processing |
| Outbox endpoint | `src/main/java/org/openpace/federation/OutboxResource.java` | C2S activity submission |
| Map service | `src/main/java/org/openpace/activity/MapImageService.java` | OSM tile compositing, track rendering |
| Map endpoint | `src/main/java/org/openpace/activity/MapResource.java` | Map/elevation/export/GPX upload |
| GPX parser | `src/main/java/org/openpace/activity/GpxService.java` | GPX XML parsing and generation |
| Federation | `src/main/java/org/openpace/federation/FederationDeliveryService.java` | Async delivery to followers |
