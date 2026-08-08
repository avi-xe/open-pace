# Frontend MVP: Federation-First Web UI

## Metadata

- **Sprint**: 6.5 (interlude between Sprint 6 and Sprint 7)
- **Estimated Time**: 6-8 hours
- **Complexity**: Medium-High
- **Dependencies**: Sprint 1-6 (all backend complete)
- **Tech Stack**: Alpine.js + Leaflet.js + Vanilla CSS (no build step)

## Goal

Build a minimal web frontend that tells the **federation story** — users immediately understand this is a federated fitness platform, not just another Strava clone. The UI stresses the ActivityPub flow: local actions ripple across the fediverse.

## Validation Against Existing Docs

This plan aligns with:

| Doc | Alignment |
|-----|-----------|
| `API_DESIGN.md` | Uses `/api/*` pattern for all app endpoints. ActivityPub endpoints remain at root for federation. |
| `DATABASE_DESIGN.md` | No schema changes — frontend consumes existing entities via new JSON endpoints. |
| `QUARKUS_TECH_STACK.md` | No new dependencies — Alpine.js/Leaflet via CDN, static files in `META-INF/resources/`. |
| `FEDERATION_DELIVERY_STRATEGY.md` | Frontend shows delivery status from existing fire-and-forget flow. |
| `MAPPING_INTEGRATION.md` | Uses existing `/api/activities/{id}/map.png` endpoint for map images. |
| `SECURITY_INTEGRATION.md` | Uses existing Basic Auth (`/api/auth/register`, `/api/auth/me`). |

## Architecture

### File Structure

```
src/main/resources/META-INF/resources/
├── index.html                      # Redirect to /app/
└── app/
    ├── index.html                  # Universe page (landing)
    ├── discover.html               # WebFinger search
    ├── feed.html                   # Activity timeline
    ├── new.html                    # Create activity + GPX upload
    ├── profile.html                # User profile
    ├── leaderboards.html           # Segment rankings
    ├── css/
    │   └── style.css               # Dark theme, federation badges
    └── js/
        ├── api.js                  # API client with Basic Auth
        ├── app.js                  # Alpine.js components + routing
        └── federation.js           # WebFinger resolver, instance map
```

### Why This Structure

- **No build step** — files served directly by Quarkus from `META-INF/resources/`
- **Alpine.js via CDN** — no npm, no bundler, no node_modules
- **Leaflet.js via CDN** — instance map visualization
- **Single-page feel** — Alpine.js `x-show` for client-side routing within each HTML file
- **Each HTML is standalone** — can be opened directly, no SPA router needed

### CDN Dependencies

```html
<!-- Alpine.js -->
<script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>

<!-- Leaflet.js -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
```

## Backend Additions Required

The existing APIs output ActivityPub format (`application/activity+json`). The frontend needs app-friendly JSON. We add a thin **AppApiResource** that wraps existing services.

### New Endpoint: `/api/federation/*`

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| `GET` | `/api/users/{username}/profile` | Profile as app JSON | No |
| `GET` | `/api/users/{username}/followers` | Followers list as app JSON | No |
| `GET` | `/api/users/{username}/following` | Following list as app JSON | No |
| `GET` | `/api/feed` | Aggregated feed from followed users | No |
| `GET` | `/api/federation/instances` | List known remote instances | No |
| `POST` | `/api/federation/follow` | Send follow to `@user@domain` | Yes |

### Existing Endpoints Used

| Endpoint | Used By |
|----------|---------|
| `POST /api/auth/register` | Login/Register page |
| `GET /api/auth/me` | All pages (current user) |
| `POST /users/{username}/outbox` | New Activity page (C2S) |
| `GET /api/activities/{id}/map.png` | Activity detail page |
| `GET /api/activities/{id}/export/gpx` | Activity detail page |
| `GET /api/segments` | Leaderboards page |
| `GET /api/segments/{id}/leaderboard` | Leaderboards page |
| `GET /.well-known/webfinger` | Discover page (WebFinger resolution) |
| `GET /users/{username}` | Profile page (ActivityPub actor) |

## Pages — Detailed Spec

### 1. Universe Page (`/app/index.html`) — Landing

**Purpose**: First impression — "this is a federated platform."

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  🌐 Open Pace — The Federated Fitness Platform   │
├──────────────────────────────────────────────────┤
│                                                  │
│  ┌─────────────────┐  ┌──────────────────────┐  │
│  │  YOUR INSTANCE   │  │  FEDERATED UNIVERSE  │  │
│  │                  │  │                      │  │
│  │  🏠 local:8080   │  │   [Leaflet Map]      │  │
│  │  5 athletes      │  │   showing instances  │  │
│  │  23 activities   │  │   as markers         │  │
│  │  3 segments      │  │                      │  │
│  └─────────────────┘  └──────────────────────┘  │
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  RECENT FEDERATED ACTIVITIES                  ││
│  │  ┌────────┐ ┌────────┐ ┌────────┐           ││
│  │  │ 🏃 Run │ │ 🚴 Ride│ │ 🏃 Run │           ││
│  │  │ 🏠 local│ │ 🌐 remote│ │ 🏠 local│         ││
│  │  └────────┘ └────────┘ └────────┘           ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  [Discover Athletes]  [View Feed]  [Leaderboards]│
└──────────────────────────────────────────────────┘
```

**Data Sources**:
- Instance stats: `GET /api/federation/instances`
- Recent activities: `GET /api/feed` (public, no auth)
- Map: Leaflet.js with instance markers

**Federation Story**: The map shows your instance connected to others. Every activity card shows a badge: 🏠 local or 🌐 remote with instance domain.

---

### 2. Discover Page (`/app/discover.html`) — WebFinger Search

**Purpose**: Find athletes across the fediverse.

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  🔍 Discover Athletes                            │
├──────────────────────────────────────────────────┤
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  Search: [@alice@mastodon.social        ] [🔍]││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  RESOLVED PROFILE                             ││
│  │  ┌──────┐                                    ││
│  │  │  🏃  │  Alice Runner                      ││
│  │  │      │  🌐 mastodon.social                 ││
│  │  └──────┘  127 followers · 45 following       ││
│  │            89 activities                       ││
│  │                                               ││
│  │  [Follow Alice]  [View Profile]  [View Feed]  ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  HOW FEDERATION WORKS:                           │
│  ┌──────────────────────────────────────────────┐│
│  │  1. You enter @alice@mastodon.social          ││
│  │  2. WebFinger resolves to actor profile       ││
│  │  3. You follow → Follow activity sent         ││
│  │  4. Alice's activities appear in your feed    ││
│  │                                               ││
│  │  [Animated flow diagram]                      ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

**Flow**:
1. User enters `@user@domain` in search box
2. Frontend calls `GET /.well-known/webfinger?resource=acct:user@domain`
3. Resolves `rel=self` link to actor profile URL
4. Fetches actor profile via `GET /api/users/{username}/profile` (if local) or raw ActivityPub URL (if remote)
5. Displays profile with Follow button
6. Follow button sends `POST /api/federation/follow` with target actor URL

**Federation Story**: The user sees WebFinger resolution in action. The "How Federation Works" section explains the flow with an animated diagram.

---

### 3. Feed Page (`/app/feed.html`) — Activity Timeline

**Purpose**: See activities from followed users (local + remote).

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  📰 Activity Feed                                │
├──────────────────────────────────────────────────┤
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  🏃 Alice — Morning 5K                        ││
│  │  🏠 local · 25:30 · 5.0km · 120m elevation   ││
│  │  ┌──────────────────┐                         ││
│  │  │  [Map Image]     │                         ││
│  │  └──────────────────┘                         ││
│  │  Published: 2 hours ago                       ││
│  │  📡 Delivered to 3 followers' inboxes         ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  🚴 Bob — Afternoon Ride                      ││
│  │  🌐 mastodon.social · 1:45:00 · 45km          ││
│  │  ┌──────────────────┐                         ││
│  │  │  [Map Image]     │                         ││
│  │  └──────────────────┘                         ││
│  │  Published: 5 hours ago                       ││
│  │  📡 Received via federation                   ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  [Load More]                                     │
└──────────────────────────────────────────────────┘
```

**Data Source**: `GET /api/feed`

**Federation Story**: Each card shows source (local/remote). Local activities show "Delivered to N followers' inboxes." Remote activities show "Received via federation." Clicking the 📡 icon shows the ActivityPub JSON.

---

### 4. New Activity Page (`/app/new.html`) — Create Activity

**Purpose**: Create activities with GPX upload.

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  ➕ New Activity                                  │
├──────────────────────────────────────────────────┤
│                                                  │
│  Activity Type: [Run ▾]                           │
│  Name: [Morning Run                    ]          │
│                                                  │
│  GPX File: [Choose File] or [Paste GPX XML]      │
│                                                  │
│  Visibility: (●) Public  (○) Unlisted  (○) Private│
│                                                  │
│  Preview:                                        │
│  ┌──────────────────────────────────────────────┐│
│  │  Distance: 5.0 km                             ││
│  │  Duration: 25:30                              ││
│  │  Pace: 5:06 /km                               ││
│  │  Elevation: +120m / -80m                      ││
│  │  Points: 156 track points                     ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  [Create Activity]                               │
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  📡 FEDERATION FLOW                           ││
│  │                                               ││
│  │  You → Outbox → Followers' Inboxes            ││
│  │  ┌────┐    ┌────┐    ┌────────────┐          ││
│  │  │ You│───▶│Local│───▶│Remote Inst.│          ││
│  │  └────┘    └────┘    └────────────┘          ││
│  │                                               ││
│  │  After posting, this activity will be         ││
│  │  delivered to your followers' inboxes.        ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

**Flow**:
1. User fills form or uploads GPX
2. Frontend parses GPX client-side for preview (using a small JS GPX parser)
3. On submit, frontend constructs ActivityPub JSON and sends to `POST /users/{username}/outbox`
4. Shows federation delivery flow diagram

**Federation Story**: The bottom panel visualizes where the activity will go — "You → Outbox → Followers' Inboxes." After posting, shows delivery status.

---

### 5. Profile Page (`/app/profile.html`) — User Profile

**Purpose**: View any user's profile with federation context.

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  👤 Alice Runner                                 │
├──────────────────────────────────────────────────┤
│                                                  │
│  ┌──────┐  Alice Runner                          │
│  │  🏃  │  🏠 local:8080                          │
│  │      │  Member since Jan 2024                  │
│  └──────┘  127 followers · 45 following           │
│                                                  │
│  [Follow]  [View Outbox]  [View Followers]        │
│                                                  │
│  ACTIVITIES (23)                                 │
│  ┌──────────────────────────────────────────────┐│
│  │  🏃 Morning 5K — 5.0km, 25:30                ││
│  │  🏃 Evening jog — 3.2km, 18:00               ││
│  │  🚴 Weekend ride — 45km, 1:45:00             ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  SEGMENTS (3 completed)                          │
│  ┌──────────────────────────────────────────────┐│
│  │  🏆 Heartbreak Hill — Best: 2:45             ││
│  │  🏆 Main Street Sprint — Best: 0:52          ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  ACTIVITYPub INSPECTOR                           │
│  ┌──────────────────────────────────────────────┐│
│  │  {                                            ││
│  │    "type": "Person",                          ││
│  │    "preferredUsername": "alice",              ││
│  │    "inbox": ".../inbox",                      ││
│  │    "outbox": ".../outbox",                    ││
│  │    "followers": ".../followers"               ││
│  │  }                                            ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

**Data Sources**:
- Profile: `GET /api/users/{username}/profile`
- Activities: `GET /api/users/{username}/activities` (existing)
- ActivityPub JSON: `GET /users/{username}` (raw ActivityPub)

**Federation Story**: Shows the raw ActivityPub actor JSON in an "Inspector" panel. Users can see exactly what other servers see.

---

### 6. Leaderboards Page (`/app/leaderboards.html`) — Rankings

**Purpose**: Segment rankings with federation context.

**Layout**:
```
┌──────────────────────────────────────────────────┐
│  🏆 Leaderboards                                 │
├──────────────────────────────────────────────────┤
│                                                  │
│  Segment: [Heartbreak Hill ▾]                     │
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │  #  Athlete        Time     Source            ││
│  │  1  Alice          2:45     🏠 local           ││
│  │  2  Bob            3:12     🌐 mastodon.social ││
│  │  3  Charlie        3:45     🏠 local           ││
│  │  4  Diana          4:01     🌐 fosstodon.org   ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  OVERALL LEADERBOARD                             │
│  ┌──────────────────────────────────────────────┐│
│  │  #  Athlete     Segments   Total Time         ││
│  │  1  Alice       12         32:15              ││
│  │  2  Bob         8          28:40              ││
│  │  3  Charlie     5          18:30              ││
│  └──────────────────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

**Data Sources**:
- Segments: `GET /api/segments`
- Leaderboard: `GET /api/segments/{id}/leaderboard`
- Overall: `GET /api/leaderboards/overall`

**Federation Story**: Each athlete shows source instance. Users see that leaderboard rankings include athletes from across the fediverse.

## Shared Components

### `api.js` — API Client

```javascript
// Wraps fetch() with:
// - Base URL handling
// - Basic Auth headers (from sessionStorage)
// - Content-Type defaults
// - Error handling
// - ActivityPub vs App JSON accept headers
```

### `federation.js` — Federation Utilities

```javascript
// WebFinger resolution:
// - resolveWebFinger(user, domain) → actor URL
// - fetchActorProfile(actorUrl) → profile JSON
//
// Instance detection:
// - getInstanceDomain(actorUrl) → domain string
// - isLocalInstance(domain) → boolean
//
// ActivityPub inspector:
// - fetchActivityPubJson(url) → raw JSON
// - formatActivityPubJson(json) → syntax-highlighted HTML
```

### `style.css` — Theme

- Dark background (#1a1a2e)
- Accent colors: blue (#4361ee) for local, green (#06d6a0) for remote
- Federation badge component: `badge-local` / `badge-remote`
- Card layout for activities
- Responsive grid for stats

## User Stories

### US-1: Universe Landing Page
**As a** visitor, **I want to** see a visual overview of the federated network **so that** I immediately understand this is a federated platform.
- **Acceptance Criteria**:
  - Landing page shows instance stats (athletes, activities, segments)
  - Leaflet map shows connected instances as markers
  - Recent activities feed shows local + remote with badges
  - Navigation to Discover, Feed, Leaderboards

### US-2: Discover Remote Athletes
**As a** user, **I want to** search for athletes on other instances using `@user@domain` **so that** I can follow people across the fediverse.
- **Acceptance Criteria**:
  - Search input accepts `@user@domain` format
  - WebFinger resolution shows resolved profile
  - Profile shows instance domain, follower count, activity count
  - Follow button sends follow request
  - "How Federation Works" explainer with flow diagram

### US-3: View Federated Feed
**As a** user, **I want to** see activities from followed users (local and remote) **so that** I can track my friends' fitness activities across the fediverse.
- **Acceptance Criteria**:
  - Feed shows activities from followed users
  - Each card shows source badge (🏠 local / 🌐 remote)
  - Local activities show delivery status (📡 Delivered to N followers)
  - Remote activities show "Received via federation"
  - Clicking 📡 shows raw ActivityPub JSON

### US-4: Create Activity with Federation Flow
**As a** user, **I want to** create activities and see the federation delivery flow **so that** I understand where my data goes.
- **Acceptance Criteria**:
  - Form supports GPX upload and manual entry
  - Preview shows parsed stats (distance, pace, elevation)
  - Visibility selector (public/unlisted/private)
  - Federation flow diagram shows delivery path
  - After posting, shows delivery status per follower

### US-5: View Profile with ActivityPub Inspector
**As a** user, **I want to** view any user's profile and see their ActivityPub data **so that** I understand how federation works at the data level.
- **Acceptance Criteria**:
  - Profile shows user info, activities, segments
  - ActivityPub Inspector panel shows raw actor JSON
  - Instance badge (local/remote) on profile
  - Follow button for remote users

### US-6: View Leaderboards
**As a** user, **I want to** see segment rankings that include athletes from across the fediverse **so that** I can compete globally.
- **Acceptance Criteria**:
  - Segment selector dropdown
  - Rankings show athlete name, time, source instance
  - Overall leaderboard across all segments
  - Source badges on each ranking entry

## Technical Stories

### TS-1: AppApiResource — JSON API Layer
**Create** `src/main/java/org/openpace/api/AppApiResource.java` with app-friendly JSON endpoints.
- `GET /api/users/{username}/profile` → `{"username", "name", "instance", "followers", "following", "activityCount"}`
- `GET /api/users/{username}/followers` → `[{"username", "instance", "actorUrl"}]`
- `GET /api/users/{username}/following` → `[{"username", "instance", "actorUrl"}]`
- `GET /api/feed` → `[{"id", "type", "author", "instance", "publishedAt", "mapUrl", "visibility"}]`
- `GET /api/federation/instances` → `[{"domain", "userCount", "lastActivity"}]`
- `POST /api/federation/follow` → sends Follow activity to remote inbox
- All under `@Path("/api")`, `@ApplicationScoped`, `@Produces(MediaType.APPLICATION_JSON)`

### TS-2: Static File Serving
**Configure** Quarkus to serve static files from `META-INF/resources/`.
- Files in `src/main/resources/META-INF/resources/app/` are served at `/app/`
- `index.html` at root redirects to `/app/`
- No additional config needed — Quarkus serves `META-INF/resources/` by default

### TS-3: `api.js` — API Client
**Create** `src/main/resources/META-INF/resources/app/js/api.js`.
- `OpenPaceApi` object with methods for each endpoint
- Basic Auth from `sessionStorage.getItem('credentials')`
- Default headers: `Accept: application/json`, `Content-Type: application/json`
- Error handling: throw on non-2xx responses
- Separate methods for ActivityPub endpoints (different Accept header)

### TS-4: `federation.js` — Federation Utilities
**Create** `src/main/resources/META-INF/resources/app/js/federation.js`.
- `resolveWebFinger(user, domain)` → fetches `/.well-known/webfinger?resource=acct:user@domain`, extracts actor URL
- `fetchActorProfile(actorUrl)` → fetches ActivityPub actor JSON
- `getInstanceDomain(actorUrl)` → extracts domain from URL
- `isLocal(actorUrl)` → compares domain to current instance
- `formatActivityPubJson(json)` → syntax-highlighted `<pre>` block

### TS-5: `style.css` — Dark Theme
**Create** `src/main/resources/META-INF/resources/app/css/style.css`.
- CSS custom properties for theming
- Dark background, light text
- Federation badge components (local/remote)
- Card layout for activities
- Map container styling
- Responsive grid for stats panels

### TS-6: Universe Page (`index.html`)
**Create** `src/main/resources/META-INF/resources/app/index.html`.
- Alpine.js `x-data` for state management
- Instance stats panel (fetched from `/api/federation/instances`)
- Leaflet map with instance markers
- Recent activities carousel
- Navigation bar with links to all pages

### TS-7: Discover Page (`discover.html`)
**Create** `src/main/resources/META-INF/resources/app/discover.html`.
- Search input with `@user@domain` parsing
- WebFinger resolution flow
- Profile display card
- Follow button
- "How Federation Works" explainer section

### TS-8: Feed Page (`feed.html`)
**Create** `src/main/resources/META-INF/resources/app/feed.html`.
- Activity card list with infinite scroll
- Source badges (local/remote)
- Map thumbnail per activity
- Delivery status indicators
- ActivityPub JSON inspector modal

### TS-9: New Activity Page (`new.html`)
**Create** `src/main/resources/META-INF/resources/app/new.html`.
- Activity type selector
- GPX file upload with client-side parsing
- Manual entry form
- Visibility selector
- Preview panel with stats
- Federation flow diagram
- Submit to outbox

### TS-10: Profile Page (`profile.html`)
**Create** `src/main/resources/META-INF/resources/app/profile.html`.
- User info panel with instance badge
- Activity list
- Segment completions
- ActivityPub Inspector panel
- Follow button (for remote users)

### TS-11: Leaderboards Page (`leaderboards.html`)
**Create** `src/main/resources/META-INF/resources/app/leaderboards.html`.
- Segment selector
- Rankings table with source badges
- Overall leaderboard
- Athlete profile links

### TS-12: AppApiResource Tests
**Create** `src/test/java/org/openpace/api/AppApiResourceTest.java`.
- Test each endpoint returns correct JSON format
- Test profile includes instance info
- Test followers/following lists
- Test feed includes source instance
- Test instances list

### TS-13: Manual Integration Testing
**Verify** the full flow in a browser:
- Register → Login → Create Activity → View in Feed
- Discover remote user → Follow → See in Feed
- View profile → See ActivityPub JSON
- View leaderboards → See cross-instance rankings

## Implementation Order

| Phase | Stories | Time | Description |
|-------|---------|------|-------------|
| **1. Backend** | TS-1, TS-12 | 1.5h | AppApiResource + tests |
| **2. Foundation** | TS-2, TS-3, TS-4, TS-5 | 1.5h | Static serving, JS utils, CSS |
| **3. Pages** | TS-6, TS-7, TS-8, TS-9, TS-10, TS-11 | 3h | All 6 pages |
| **4. Integration** | TS-13 | 1h | End-to-end testing |
| **5. Polish** | — | 1h | Animations, error states, loading states |

## What This Skips (Intentionally)

- No ActivityPub S2S signature verification UI
- No real-time WebSocket updates
- No mobile responsiveness (desktop-first)
- No OAuth — Basic Auth only
- No build step or bundler
- No TypeScript — plain JS
- No frontend framework beyond Alpine.js
- No unit tests for JS (manual testing only)

## Next Steps After Frontend MVP

- Sprint 7: PostGIS + advanced mapping
- Sprint 8: Analytics & personal records
- Sprint 9: Social interactions (comments, kudos)
- Sprint 10: Gear tracking
- Future: Real-time updates, mobile app, OAuth
