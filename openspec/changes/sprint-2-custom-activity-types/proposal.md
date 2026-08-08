# Sprint 2: Custom Activity Types with DDD Restructure

## Problem Statement

Sprint 1 delivered a basic ActivityPub server with all code in a single `org.openpace.core` package. As we add custom activity types (Run, Ride, Swim, etc.), the monolithic package structure will become unmaintainable. We need to:

1. **Restructure into DDD bounded contexts** for clean separation of concerns
2. **Add custom activity types** with JSONB storage for flexible schemas
3. **Provide API endpoints** for UI-friendly access alongside ActivityPub endpoints

## Proposed Solution

### DDD Package Restructure

Move Sprint 1 code into bounded contexts:

- `actor/` — Actor entity and ActivityPub endpoints
- `activity/` — Activity entity, types, repository, service, and endpoints
- `social/` — Follower entity and ActivityPub endpoints
- `federation/` — Federation delivery, inbox, outbox
- `webfinger/` — WebFinger discovery
- `shared/` — Cross-cutting concerns (error handling, application entry point)

### Custom Activity Types

Add support for sports activity types:

- `Run` — Running activities with distance, duration, pace
- `Ride` — Cycling activities with distance, duration, speed
- `Swim` — Swimming activities with distance, duration, pace
- `Walk` — Walking activities with distance, duration
- `Hike` — Hiking activities with distance, duration, elevation

### JSONB Storage

Store custom activity objects as JSONB to preserve all properties without schema changes.

### API Endpoints

Add `/api/activities` endpoints for UI-friendly access with standard JSON responses.

## Alternatives Considered

1. **Keep single package** — Simpler but doesn't scale
2. **Separate microservices** — Over-engineered for this stage
3. **Add columns for each type** — Inflexible, requires schema changes for new types

## Recommendation

DDD bounded contexts + JSONB storage. This gives us clean separation while keeping the flexibility to add new activity types without schema changes.

## Scope

- DDD package restructure
- ActivityType enum
- ActivityRepository with query methods
- ActivityService with JSONB support
- V2 migration for JSONB column
- /api/activities endpoints
- Tests for all new components
