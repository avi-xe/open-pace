# Sprint 7.5: Consolidation Sprint

## Metadata
- **Sprint Number**: 7.5
- **Estimated Time**: 8-10 hours
- **Complexity**: High
- **Dependencies**: Sprints 1-7 (MVP Foundation)

## Objectives

1. Fix critical bugs (federation delivery, transaction boundaries)
2. Refactor to proper DDD (split god classes, create repositories, add value objects)
3. Establish TDD foundation (unit tests for pure domain logic)
4. Performance fixes (N+1 lazy loading)
5. Align with architecture decisions (C2S first, OAM, federation summaries)

## Architecture Decisions (from Sprint 7.5 Discussion)

| Decision | Choice | Notes |
|----------|--------|-------|
| **C2S Priority** | C2S first, Strava REST later | ActivityPub C2S as primary client interface |
| **OAM/SSI** | Decentralized sign-in (Mastodon-style) | OIDC or OAuth2 flow with remote instances |
| **Federation Scope** | Activity summaries only | No GPS traces, no raw data federated |
| **Deployment** | Both single-instance and self-hosted | Docker compose for easy setup |
| **UI Framework** | Federation-first, Strava-like | No themes for now |
| **Maps** | MapLibre GL (detail), static images (sharing) | Lightweight, performant |
| **Charts** | Pace/elevation only | Activity detail view |
| **Accessibility** | Basic WCAG compliance | Expand later |

## Phase 1: Critical Fixes (2-3 hours)

### 1.1 Fix FederationDeliveryService.deliver() Bug
**Problem**: `deliver()` discards activity JSON — sends empty objects to remote inboxes.
**Fix**: Use `activityJson` parameter instead of re-serializing from entity.

**Files**:
- `src/main/java/org/openpace/federation/FederationDeliveryService.java`

### 1.2 Fix Transaction Boundaries
**Problem**: Federation delivery inside `@Transactional` — network failure rolls back local activity.
**Fix**: Move federation delivery outside transaction boundary using `@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)` or event-based async.

**Files**:
- `src/main/java/org/openpace/federation/OutboxResource.java`
- `src/main/java/org/openpace/activity/ActivityService.java`

### 1.3 Fix Activity ID Generation
**Problem**: `System.currentTimeMillis()` for activity ID — collision risk under concurrency.
**Fix**: Use UUID or database sequence.

**Files**:
- `src/main/java/org/openpace/activity/Activity.java`
- `src/main/java/org/openpace/activity/ActivityService.java`

### 1.4 Add @Transactional to ActivityService.createActivity()
**Problem**: Missing transaction boundary for activity creation.
**Fix**: Add `@Transactional` annotation.

**Files**:
- `src/main/java/org/openpace/activity/ActivityService.java`

## Phase 2: DDD Refactoring (3-4 hours)

### 2.1 Split ActivityPubService (God Class)
**Problem**: 200+ lines, 15+ methods, injected by 6 classes across 5 packages.
**Fix**: Split into focused services:
- `InboxActivityProcessor` — handles inbound ActivityPub activities
- `ActivityPubModelBuilder` — builds AS2 collections and objects
- `ActivityDomainMapper` — converts between domain and protocol models

**Files**:
- `src/main/java/org/openpace/activity/ActivityPubService.java` (refactor)
- `src/main/java/org/openpace/federation/InboxActivityProcessor.java` (new)
- `src/main/java/org/openpace/federation/ActivityPubModelBuilder.java` (new)
- `src/main/java/org/openpace/federation/ActivityDomainMapper.java` (new)

### 2.2 Create Repository Abstractions
**Problem**: No `ActorRepository`, `FollowerRepository`, `UserRepository` — all use static entity methods.
**Fix**: Create repository interfaces and implementations.

**Files**:
- `src/main/java/org/openpace/actor/ActorRepository.java` (new)
- `src/main/java/org/openpace/social/FollowerRepository.java` (new)
- `src/main/java/org/openpace/auth/UserRepository.java` (new)
- Update all callers to use repositories

### 2.3 Route All Queries Through Repositories
**Problem**: `ActivityRepository` bypassed by 4+ resources using `Activity.find()` directly.
**Fix**: Move all queries to repository methods.

**Files**:
- `src/main/java/org/openpace/federation/OutboxResource.java`
- `src/main/java/org/openpace/api/AppApiResource.java`
- `src/main/java/org/openpace/activity/MapResource.java`
- `src/main/java/org/openpace/activity/ExportResource.java`

### 2.4 Convert Activity.visibility to Enum
**Problem**: Raw String, no type safety.
**Fix**: Create `Visibility` enum with `PUBLIC`, `FOLLOWERS`, `PRIVATE`.

**Files**:
- `src/main/java/org/openpace/activity/Visibility.java` (new)
- `src/main/java/org/openpace/activity/Activity.java`
- Update all callers

### 2.5 Move ActivityPubModels to federation.protocol
**Problem**: Wrong package, used by 5+ packages.
**Fix**: Move to `org.openpace.federation.protocol`.

**Files**:
- `src/main/java/org/openpace/activity/models/ActivityPubModels.java` (move)
- Update all imports

## Phase 3: TDD Foundation (2-3 hours)

### 3.1 Unit Tests for GpxService
**Problem**: Pure math logic (Haversine, elevation) has zero tests.
**Fix**: Add unit tests for all public methods.

**Files**:
- `src/test/java/org/openpace/activity/GpxServiceTest.java` (new)

### 3.2 Unit Tests for matchAndRecordEfforts()
**Problem**: Complex GPS-matching logic is untested.
**Fix**: Add unit tests with known GPS coordinates and expected matches.

**Files**:
- `src/test/java/org/openpace/segment/SegmentServiceTest.java` (new)

### 3.3 Extract Shared Utilities
**Problem**: Duplicate code: `convertGpxDataToJsonNode()`, `parseTrackData()`.
**Fix**: Extract to `GpxUtils` utility class.

**Files**:
- `src/main/java/org/openpace/shared/GpxUtils.java` (new)
- Update `ActivityService`, `MapResource`, `ExportResource`

## Phase 4: Performance (1 hour)

### 4.1 Fix N+1 Lazy Loading
**Problem**: `OutboxResource.getOutbox()`, `SegmentService.getLeaderboard()` have N+1 queries.
**Fix**: Use JOIN FETCH in JPQL queries.

**Files**:
- `src/main/java/org/openpace/activity/ActivityRepository.java`
- `src/main/java/org/openpace/segment/SegmentService.java`

## Acceptance Criteria

### Critical Fixes
- [ ] Federation delivery sends correct activity JSON
- [ ] Activity creation succeeds even if federation fails
- [ ] No activity ID collisions under concurrent load
- [ ] Activity creation is atomic (all-or-nothing)

### DDD Refactoring
- [ ] `ActivityPubService` split into 3+ focused classes
- [ ] All persistence access goes through repositories
- [ ] `Activity.visibility` is type-safe enum
- [ ] `ActivityPubModels` in correct package
- [ ] No god classes (>200 lines, >15 methods)

### TDD Foundation
- [ ] `GpxService` has 100% unit test coverage
- [ ] `matchAndRecordEfforts()` has unit tests
- [ ] All duplicated code extracted to utilities
- [ ] Unit tests run in <100ms (no container)

### Performance
- [ ] No N+1 lazy loading in hot paths
- [ ] Outbox endpoint loads in <50ms

### Regression
- [ ] All 111 existing tests pass
- [ ] Federation delivery works end-to-end
- [ ] Spatial queries still work

## Testing Strategy

### Unit Tests (New)
- `GpxServiceTest` — pure math, no container
- `SegmentServiceTest` — GPS matching logic
- `VisibilityTest` — enum behavior

### Integration Tests (Existing)
- All existing `@QuarkusTest` classes
- Add federation delivery integration test

### Manual Testing
- Register user via OAM
- Create activity with GPX
- Verify federation delivery to remote instance
- Verify spatial queries

## Out of Scope

- UI/UX implementation (next sprint)
- MapLibre GL integration (next sprint)
- OAM/OIDC implementation (Sprint 8)
- Strava REST API compatibility (Sprint 9)
