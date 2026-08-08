# Repository Guidelines

## Project Overview

**Open Pace** is a federated fitness platform (Strava alternative) built on ActivityPub. It enables ActivityPub C2S (client-to-server) and S2S (server-to-server) federation, allowing users to share fitness activities across the Fediverse.

- **Language**: Java 21
- **Framework**: Quarkus 3.30.6
- **Database**: PostgreSQL with Flyway migrations
- **Build**: Maven 3.9.11 (via wrapper)
- **Async**: Vert.x for federation delivery

## Architecture & Data Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  Outbox     │────▶│  Activity   │
│  (ActivityPub) │  │  Resource   │     │  Service    │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                    │
                           ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐
                    │ Federation  │     │  Activity   │
                    │  Delivery   │     │  Repository │
                    │  (Vert.x)   │     │  (Panache)  │
                    └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Remote     │
                    │  Inboxes    │
                    └─────────────┘
```

**Core Flow**:
1. Client POSTs ActivityPub activity to `/users/{username}/outbox`
2. `OutboxResource` validates and persists via `ActivityService`
3. `ActivityService` stores with dual-storage: `objectContent` for Notes, `objectJson` (JSONB) for custom sports
4. `FederationDeliveryService` fans out to followers' inboxes via Vert.x WebClient
5. Inbound activities processed by `InboxResource` → `ActivityPubService.processActivity()`

**Key Packages** (`org.openpace`):
- `activity` - Activity entities, services, ActivityPub models
- `actor` - Actor (user) entities and endpoints
- `federation` - Inbox/Outbox resources, delivery service
- `social` - Follower entities, followers/following endpoints
- `webfinger` - WebFinger discovery (`/.well-known/webfinger`)
- `shared` - Common exceptions, application entry

## Key Directories

```
src/main/java/org/openpace/
├── activity/           # Activity entities, services, ActivityPub models
│   ├── models/         # ActivityPub POJOs (ActivityPubModels.java)
├── actor/              # Actor (user) entities and endpoints
├── federation/         # Inbox/Outbox resources, delivery service
├── social/             # Follower entities, followers/following
├── webfinger/          # WebFinger discovery
└── shared/             # Common exceptions, Application.java entry

src/main/resources/
├── db/migration/       # Flyway SQL migrations (V1__, V2__, ...)
└── application.properties

src/test/java/org/openpace/
├── activity/           # Activity tests
├── actor/              # Actor tests
├── federation/         # Inbox/Outbox tests
├── social/             # Follower tests
└── webfinger/          # WebFinger tests

docs/                   # 22 strategy/reference documents
```

## Development Commands

```bash
# Development server (hot reload)
./mvnw quarkus:dev

# Run all tests
./mvnw test

# Run integration tests only
./mvnw verify -Pfailsafe

# Package application
./mvnw package

# Native build (requires GraalVM)
./mvnw package -Pnative
```

**Database**: Quarkus Dev Services auto-provisions PostgreSQL in dev mode. No manual DB setup needed.

## Code Conventions & Common Patterns

### Naming Conventions
- **Entities**: Singular nouns (`Activity`, `Actor`, `Follower`)
- **Resources**: Suffix with `Resource` (`ActivityResource`, `OutboxResource`)
- **Services**: Suffix with `Service` (`ActivityService`, `FederationDeliveryService`)
- **Repositories**: Suffix with `Repository` (`ActivityRepository`)

### Entity Pattern
```java
@Entity
public class Activity extends PanacheEntityBase {
    // Fields, finders, business methods
    public static Activity findByActivityId(String activityId) { ... }
}
```

### Resource Pattern (JAX-RS)
```java
@Path("/users/{username}/outbox")
@ApplicationScoped
public class OutboxResource {
    @POST
    @Transactional
    public Response postActivity(...) { ... }
}
```

### Service Pattern
```java
@ApplicationScoped
public class ActivityService {
    @Inject
    ActivityRepository activityRepository;
    
    public Activity createActivity(...) { ... }
}
```

### Transaction Patterns
- `@TestTransaction` - Auto-rollback for entity/repository/service tests
- Manual `UserTransaction` - Commit before REST Assured calls in resource tests

### ActivityPub Models
- POJOs in `ActivityPubModels.java` with Jackson annotations
- Constants for type strings (`TYPE_NOTE = "Note"`)
- `@JsonProperty` for ActivityPub field mapping
- `@JsonInclude(NON_NULL)` to omit empty fields

### Error Handling
- Custom exceptions in `shared/` package
- Exception mappers for consistent error responses
- Validation via Hibernate Validator annotations

## Important Files

### Entry Points
- `src/main/java/org/openpace/shared/Application.java` - Quarkus application entry
- `src/main/resources/application.properties` - Main configuration

### Configuration
- `pom.xml` - Maven build config, dependencies, plugins
- `src/main/resources/application.properties` - Quarkus config (datasource, Jackson, HTTP port)
- `.mvn/wrapper/maven-wrapper.properties` - Maven wrapper version

### Key Modules
- `src/main/java/org/openpace/activity/ActivityPubService.java` - Core federation logic
- `src/main/java/org/openpace/activity/models/ActivityPubModels.java` - ActivityPub POJOs
- `src/main/java/org/openpace/federation/FederationDeliveryService.java` - Async delivery via Vert.x

### Database
- `src/main/resources/db/migration/V1__initial_schema.sql` - actors, activities, followers tables
- `src/main/resources/db/migration/V2__add_jsonb_column.sql` - JSONB column for custom sports

## Runtime/Tooling Preferences

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | Required |
| Maven | 3.9.11 | Via `./mvnw` wrapper |
| Quarkus | 3.30.6 | Primary framework |
| PostgreSQL | - | Production DB (auto-provisioned in dev) |
| H2 | - | Test DB only |
| Flyway | - | Schema migrations |
| Vert.x | - | Async HTTP for federation |

**Package Manager**: Maven (no npm/gradle)
**Frontend**: Static HTML only (`index.html`), no build pipeline
**Native Build**: GraalVM required for `-Pnative`

## Testing & QA

### Framework
- **JUnit 5** (Jupiter) via `quarkus-junit5`
- **REST Assured** for HTTP integration testing
- **H2 in-memory** database for test isolation

### Running Tests
```bash
# All tests
./mvnw test

# Unit tests only (Surefire)
./mvnw surefire:test

# Integration tests only (Failsafe)
./mvnw verify -Pfailsafe
```

### Test Organization
```
src/test/java/org/openpace/
├── activity/
│   ├── ActivityTest.java           # Entity CRUD (5 tests)
│   ├── ActivityTypeTest.java       # Enum tests (7 tests)
│   ├── ActivityRepositoryTest.java # Repository queries (5 tests)
│   ├── ActivityServiceTest.java    # Service logic (5 tests)
│   └── ActivityResourceTest.java   # REST integration (2 tests)
├── actor/
│   ├── ActorTest.java              # Entity CRUD (6 tests)
│   └── ActorResourceTest.java      # REST integration (3 tests)
├── federation/
│   ├── OutboxResourceTest.java     # Outbox REST (4 tests)
│   └── InboxResourceTest.java      # Inbox REST (4 tests)
├── social/
│   ├── FollowerTest.java           # Entity tests (5 tests)
│   └── FollowersResourceTest.java  # REST integration (5 tests)
└── webfinger/
    └── WebFingerResourceTest.java  # REST integration (6 tests)
```

### Test Conventions
- **Unique prefixes**: Each test class uses distinct username prefixes (`atest-`, `actres-`, `svc-`, `repo-`, `fed-`, `social-`, `wf-`)
- **Transaction handling**: `@TestTransaction` for auto-rollback, `UserTransaction` for committed state in REST tests
- **No mocking**: Pure Quarkus CDI injection with H2 in-memory DB
- **Content types**: REST tests validate `application/activity+json`
- **Self-contained**: No shared fixtures or base test classes

### Coverage
- ~57 test methods across 13 test files
- 5 domain packages covered
- No coverage reporting tools configured

### Contribution Standards
- Conventional commits required
- Branch naming: `feature/sprint-X`, `fix/description`
- See `CONTRIBUTING.md` for full guidelines
