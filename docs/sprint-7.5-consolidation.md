# Sprint 7.5: Consolidation Sprint ✅ COMPLETE

## Metadata
- **Sprint Number**: 7.5
- **Estimated Time**: 8-10 hours (actual: ~6 hours)
- **Complexity**: High
- **Dependencies**: Sprints 1-7 (MVP Foundation)
- **Status**: ✅ Complete (132 tests passing)

## Objectives

1. Fix critical bugs (federation delivery, transaction boundaries)
2. Refactor to proper DDD (split god classes, create repositories, add value objects)
3. Establish TDD foundation (unit tests for pure domain logic)
4. Performance fixes (N+1 lazy loading)
5. Align with architecture decisions (C2S first, OAM, federation summaries)

## Completed Work

### Phase 1: Critical Fixes ✅
- [x] Fixed `FederationDeliveryService.deliver()` bug (was sending empty objects)
- [x] Fixed transaction boundaries (delivery outside `@Transactional`)
- [x] UUID for activity IDs instead of `System.currentTimeMillis()`
- [x] Added `@Transactional` to `ActivityService.createActivity()`

### Phase 2: DDD Refactoring ✅
- [x] Split `ActivityPubService` into 3 focused services
- [x] Created `ActorRepository`, `FollowerRepository`, `UserRepository`
- [x] Converted `Activity.visibility` to type-safe `Visibility` enum
- [x] Moved `ActivityPubModels` to `federation.protocol` package

### Phase 3: TDD Foundation ✅
- [x] Added 13 unit tests for `GpxService` (pure math, no container)
- [x] Added 10 unit tests for `SegmentService` (GPS matching logic)
- [x] Extracted `GpxUtils` shared utility (eliminated duplicate code)

### Phase 4: Performance ✅
- [x] Added `JOIN FETCH` to prevent N+1 lazy loading in `ActivityRepository`

## Test Coverage

- **109 integration tests** + **23 unit tests** = **132 tests passing**
- Unit tests run in <15ms (no container)
- All commits follow conventional commit format

## Architecture Decisions

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

## Library Research

### Recommended Libraries (for Sprint 8.5)

| Library | Stars | License | Purpose |
|---------|-------|---------|---------|
| **tomitribe-http-signatures** | 93 | Apache 2.0 | HTTP Signature signing/verification |
| **jsonld-java** | 388 | BSD 3-Clause | JSON-LD @context processing |

### Why These Libraries

**tomitribe-http-signatures**:
- Battle-tested with Mastodon (draft-cavage-http-signatures)
- Simple `Signer`/`Verifier` API
- Framework-agnostic (works with Quarkus)

**jsonld-java**:
- Only mature JSON-LD library in Java
- Needed for @context expansion if servers send complex contexts

### Keep Custom

| Component | Reason |
|-----------|--------|
| ActivityPubModels | Domain-specific, enhance with missing fields |
| WebFingerResource | Already correct (~30 lines, no library exists) |
| ActivityPubModelBuilder | Domain-specific mapping |
| InboxActivityProcessor | Business logic |

## Next Sprint

**Sprint 8.5: Federation Protocol & HTTP Signatures** — [View Document](sprint-8.5-federation-protocol.md)

## Git Log

```
362b607 test(sprint7.5): add SegmentService unit tests
a7376b0 refactor(sprint7.5): move ActivityPubModels to federation.protocol
0e636e4 refactor(sprint7.5): extract GpxUtils shared utility
9b9bc67 perf(sprint7.5): add JOIN FETCH to prevent N+1 lazy loading
df84e28 test(sprint7.5): add GpxService unit tests
54a3175 refactor(sprint7.5): convert Activity.visibility to Visibility enum
df5c5dd refactor(sprint7.5): create repository abstractions
ba7c761 refactor(sprint7.5): split ActivityPubService and fix critical bugs
```
