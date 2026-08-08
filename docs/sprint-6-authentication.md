# Sprint 6: Authentication & Authorization

## Metadata

- **Sprint Number**: 6
- **Estimated Time**: 4-5 hours
- **Complexity**: High
- **Dependencies**: Sprint 1-5 (all previous)

## Implementation Goals

1. User registration with password hashing (BCrypt)
2. HTTP Basic Authentication on protected endpoints
3. Actor-User linkage (one-to-one)
4. Secure outbox POST (only actor can post to their own outbox)
5. User info endpoint (`/api/auth/me`)

## What Gets Implemented

Users can register with a username and password. Passwords are hashed with BCrypt. The outbox POST endpoint requires authentication — only the actor can post activities to their own outbox. A `/api/auth/me` endpoint returns the authenticated user's info. The Actor entity gains a link to the User entity for the ActivityPub identity/auth relationship.

## Security Model

| Endpoint | Auth Required | Notes |
|----------|---------------|-------|
| `GET /.well-known/webfinger` | No | Public discovery |
| `GET /users/{username}` | No | Public profile |
| `GET /users/{username}/outbox` | No | Public activity stream |
| `POST /users/{username}/outbox` | **Yes** | Only actor can post |
| `POST /api/auth/register` | No | Public registration |
| `GET /api/auth/me` | **Yes** | Current user info |
| `POST /users/{username}/inbox` | No | S2S (future: HTTP signatures) |

## Implementation Steps

### Step 1: Add Dependency

`quarkus-security-jpa` — includes BCrypt support out of the box.

### Step 2: Database Migration

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT true,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE actors ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_actors_user_id ON actors(user_id);
```

### Step 3: User Entity

Uses Quarkus Security JPA annotations: `@UserDefinition`, `@Username`, `@Password`, `@Roles`.

### Step 4: AuthResource

- `POST /api/auth/register` — create user + actor
- `GET /api/auth/me` — return authenticated user

### Step 5: Secure Outbox

Add `@RolesAllowed("user")` to outbox POST. Verify authenticated username matches path username.

## Next Steps

- ✅ You've completed Sprint 6!
- 🎯 **Next**: Sprint 7: Mapping Integration & PostGIS
