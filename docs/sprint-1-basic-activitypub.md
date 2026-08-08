# Sprint 1: Basic ActivityPub Server

## Metadata

- **Sprint Number**: 1
- **Estimated Time**: 4-6 hours
- **Complexity**: Medium
- **Dependencies**: None (first sprint)

## Implementation Goals

1. Create a functional Quarkus project with proper dependencies
2. Implement core ActivityPub endpoints (WebFinger, Actor, Inbox, Outbox, Followers)
3. Establish database schema with Flyway migrations
4. Build ActivityPub JSON serialization with `application/activity+json` content type
5. Implement error handling with consistent error responses

## Prerequisites Checklist

- [x] Java 21+ installed
- [x] Maven 3.8+ installed
- [x] Docker/Podman running (for Quarkus Dev Services)
- [x] Git configured

## What Gets Implemented

A minimal ActivityPub server that can:

- Discover actors via WebFinger (`/.well-known/webfinger`)
- Serve actor profiles as `application/activity+json`
- Accept activities via inbox (S2S delivery)
- Submit activities via outbox (C2S pattern)
- List followers and following as OrderedCollections
- Serve individual activities by ID
- Handle errors consistently with JSON error responses

## Architecture Changes

### New Components

- **Quarkus project** with pom.xml, Maven wrapper, application.properties
- **Database layer**: Actor, Activity, Follower entities with Panache
- **Service layer**: ActivityPubService, FederationDeliveryService
- **REST resources**: WebFingerResource, ActorResource, InboxResource, OutboxResource, FollowersResource, ActivityResource
- **Error handling**: ErrorResponse, ActivityPubException, GlobalExceptionMapper, ValidationExceptionMapper

### Package Structure

```
org.openpace.core
├── Actor.java                    # Actor entity
├── Activity.java                 # Activity entity
├── Follower.java                 # Follower entity
├── ActivityPubModels.java        # JSON model classes
├── ActivityPubService.java       # Core service
├── FederationDeliveryService.java # Delivery via Vert.x
├── WebFingerResource.java        # /.well-known/webfinger
├── ActorResource.java            # /users/{username}
├── InboxResource.java            # /users/{username}/inbox
├── OutboxResource.java           # /users/{username}/outbox
├── FollowersResource.java        # /users/{username}/followers|following
├── ActivityResource.java         # /activities/{activityId}
├── ErrorResponse.java            # Error response model
├── ActivityPubException.java     # Custom exception
├── GlobalExceptionMapper.java    # Unhandled exceptions
├── ValidationExceptionMapper.java # Bean Validation errors
├── ActivityPubExceptionMapper.java # ActivityPub errors
└── OpenPaceApplication.java      # JAX-RS application
```

## Implementation Steps

### Step 1: Project Scaffolding

- **Time**: 30 minutes
- **Goal**: Create Quarkus project structure
- **Code Changes**: pom.xml, .mvn/wrapper/, src/main/resources/application.properties
- **Key Concepts**: Quarkus BOM, dependency management, Dev Services
- **Test**: `./mvnw quarkus:dev` starts without errors

### Step 2: Database Schema

- **Time**: 30 minutes
- **Goal**: Define entities and Flyway migration
- **Code Changes**: Actor.java, Activity.java, Follower.java, V1__initial_schema.sql
- **Key Concepts**: Panache entities, Flyway migrations, indexing
- **Test**: Application starts, tables created in PostgreSQL

### Step 3: ActivityPub Service Layer

- **Time**: 1 hour
- **Goal**: Build core service for activity processing and JSON serialization
- **Code Changes**: ActivityPubService.java, ActivityPubModels.java
- **Key Concepts**: ActivityPub JSON-LD, OrderedCollection, Actor model
- **Test**: Service methods build correct JSON structures

### Step 4: REST Resources

- **Time**: 1.5 hours
- **Goal**: Implement all ActivityPub endpoints
- **Code Changes**: WebFingerResource, ActorResource, InboxResource, OutboxResource, FollowersResource, ActivityResource
- **Key Concepts**: JAX-RS path routing, content negotiation, @PathParam
- **Test**: All endpoints respond with correct status codes and content types

### Step 5: Error Handling

- **Time**: 30 minutes
- **Goal**: Consistent error responses across all endpoints
- **Code Changes**: ErrorResponse, ActivityPubException, GlobalExceptionMapper, ValidationExceptionMapper, ActivityPubExceptionMapper
- **Key Concepts**: ExceptionMapper, Bean Validation, error response format
- **Test**: Invalid requests return proper JSON error responses

### Step 6: Federation Delivery

- **Time**: 1 hour
- **Goal**: Deliver activities to remote inboxes via Vert.x WebClient
- **Code Changes**: FederationDeliveryService.java
- **Key Concepts**: Vert.x WebClient, async HTTP, fire-and-forget delivery
- **Test**: Creating a note delivers to followers' inboxes

## Code Structure

### New Packages

```
org.openpace.core
  ├── [Entity] Actor, Activity, Follower
  ├── [Model] ActivityPubModels
  ├── [Service] ActivityPubService, FederationDeliveryService
  ├── [Resource] WebFingerResource, ActorResource, InboxResource, OutboxResource, FollowersResource, ActivityResource
  ├── [Error] ErrorResponse, ActivityPubException, *ExceptionMapper
  └── [App] OpenPaceApplication
```

### Configuration Changes

- `application.properties`: openpace.domain, openpace.scheme, quarkus.datasource, quarkus.flyway
- `pom.xml`: All Sprint 1 dependencies

## Testing Strategy

### Unit Tests

- [ ] Test Actor entity finders
- [ ] Test Activity entity finders
- [ ] Test ActivityPubService JSON builders

### Integration Tests

- [ ] Test WebFinger discovery
- [ ] Test Actor profile endpoint
- [ ] Test Outbox submission and retrieval
- [ ] Test Inbox activity processing
- [ ] Test Followers collection
- [ ] Test error responses for invalid requests

### Manual Tests

- [ ] WebFinger: `curl "http://localhost:8080/.well-known/webfinger?resource=acct:alice@localhost:8080"`
- [ ] Actor: `curl -H "Accept: application/activity+json" http://localhost:8080/users/alice`
- [ ] Outbox: `curl -X POST -H "Content-Type: application/activity+json" -d '{"type":"Create","object":{"type":"Note","content":"Hello World"}}' http://localhost:8080/users/alice/outbox`
- [ ] Mastodon compatibility: Can Mastodon discover and follow this actor?

## Key Concepts to Document

### ActivityPub C2S Pattern

- **What**: Client-to-server activity submission via POST to outbox
- **Why**: Allows clients to publish activities without knowing federation details
- **How**: POST JSON to /users/{username}/outbox, process by type field
- **Example**: `{"type":"Create","object":{"type":"Note","content":"Hello"}}`

### WebFinger Discovery

- **What**: RFC 7033 protocol for discovering user information
- **Why**: Required for ActivityPub actor discovery (how Mastodon finds users)
- **How**: GET /.well-known/webfinger?resource=acct:user@domain
- **Example**: Returns JRD document with actor URL

### OrderedCollection

- **What**: ActivityStreams collection type for ordered items
- **Why**: Used for outbox, followers, following lists
- **How**: Returns totalItems count and link to first page
- **Example**: `{"type":"OrderedCollection","totalItems":42,"first":"..."}`

## Common Pitfalls

### Pitfall 1: Content-Type Negotiation

- **Why it happens**: Browsers send Accept: text/html, not application/activity+json
- **How to avoid**: Use curl with -H "Accept: application/activity+json"
- **How to fix**: Ensure endpoints produce correct content type

### Pitfall 2: Activity ID Stability

- **Why it happens**: Generating IDs that change on each request
- **How to avoid**: Use stable IDs based on database ID or UUID
- **How to fix**: Store activity ID in database, never regenerate

### Pitfall 3: N+1 Query Problem

- **Why it happens**: Loading activities and their actors separately
- **How to avoid**: Use JOIN FETCH in queries
- **How to fix**: Add fetch joins to Panache queries

## ActivityPub Compliance Notes

- [ ] Follows ActivityPub C2S specification
- [ ] JSON-LD context correct (<https://www.w3.org/ns/activitystreams>)
- [ ] Content-Type headers correct (application/activity+json)
- [ ] Status codes correct (202 Accepted for inbox/outbox POST)
- [ ] WebFinger returns correct JRD format

## Performance Considerations

- [ ] Vert.x WebClient for non-blocking federation delivery
- [ ] Panache lazy loading for entity relationships
- [ ] Connection pooling for PostgreSQL
- [ ] Async processing for activity delivery

## Implementation Decisions to Document

- [ ] Use Panache Active Record pattern for entities
- [ ] Fire-and-forget delivery (Sprint 1) vs reliable delivery (Sprint 5)
- [ ] Dual storage: object_content (TEXT) for Notes, object_json (JSONB) for custom types
- [ ] ActivityPub endpoints at root level, app endpoints under /api/*

## Transition to Next Sprint

- **What's missing**: Custom activity types (Run, Ride), JSONB storage, activity aggregation
- **Teaser**: Sprint 2 adds RunActivity and RideActivity with GPS data support

## Review Checklist

1. [ ] All endpoints respond with correct status codes
2. [ ] ActivityPub content type negotiation works
3. [ ] WebFinger discovery returns correct JRD
4. [ ] Actor profile includes all required fields
5. [ ] Outbox accepts and stores activities
6. [ ] Inbox processes incoming activities
7. [ ] Followers collection returns correct data
8. [ ] Error responses follow consistent format
9. [ ] Database migrations run successfully
10. [ ] Federation delivery works (if test instance available)
