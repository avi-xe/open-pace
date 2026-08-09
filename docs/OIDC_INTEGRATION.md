# OIDC Integration Strategy

This document defines the OpenID Connect (OIDC) authentication strategy for Open Pace, replacing the previous Basic Auth approach.

## Overview

Open Pace uses OIDC for user authentication via external identity providers. This enables users to log in with their existing accounts (e.g., Keycloak, Mastodon) rather than creating separate credentials.

**Supported Providers:**
1. **Generic OIDC** — Any standards-compliant OIDC provider (Keycloak, Auth0, Google, etc.)
2. **Mastodon OAuth2** — Dynamic per-instance OAuth2 (Mastodon instances are OAuth2-only, not OIDC)

## Architecture

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Browser    │────▶│  Open Pace      │────▶│  OIDC Provider   │
│              │◀────│  (OIDC Client)  │◀────│  (Keycloak, etc) │
└──────────────┘     └─────────────────┘     └──────────────────┘
                            │
                            ▼
                     ┌─────────────────┐
                     │  PostgreSQL     │
                     │  users +        │
                     │  external_id    │
                     └─────────────────┘
```

## Flow

### Generic OIDC Login
1. User clicks "Login with OIDC"
2. Browser redirects to Open Pace `/api/auth/login/generic`
3. Quarkus OIDC redirects to the configured provider (e.g., Keycloak)
4. User authenticates at the provider
5. Provider redirects back with authorization code
6. Quarkus exchanges code for tokens, creates session
7. `OidcAuthObserver` fires → creates User + Actor + ExternalIdentity if first login
8. User is authenticated, session cookie set

### Mastodon OAuth Login
1. User clicks "Login with Mastodon" and provides instance URL
2. `MastodonOAuthService` registers an OAuth app with the instance (`POST /api/v1/apps`)
3. User is redirected to instance's OAuth authorize endpoint
4. User authenticates at the instance
5. Instance redirects back with authorization code
6. Code is exchanged for access token
7. User info fetched from `/api/v1/accounts/verify_credentials`
8. `OidcAuthObserver` fires → creates User + Actor + ExternalIdentity

## Key Classes

| Class | Purpose |
|---|---|
| `OidcTenantResolver` | Resolves OIDC tenant config per request (generic vs Mastodon) |
| `MastodonOAuthService` | Dynamic app registration + tenant config for Mastodon instances |
| `OidcAuthObserver` | Observes successful OIDC auth → creates local User/Actor/Identity |
| `LoginResource` | `/api/auth/login` — lists providers, initiates flows |
| `AuthResource` | `/api/auth/me` — returns current user from OIDC token |

## Database Schema

### users table
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),
    display_name VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT true,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### external_identity table
```sql
CREATE TABLE external_identity (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    username VARCHAR(255),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);
```

## Configuration

### application.properties (production)
```properties
# OIDC Provider
quarkus.oidc.auth-server-url=https://keycloak.example.com/realms/openpace
quarkus.oidc.client-id=open-pace
quarkus.oidc.credentials.secret=<secret>
quarkus.oidc.application-type=web-app
quarkus.oidc.authentication.redirect-path=/api/auth/callback
quarkus.oidc.logout.path=/api/auth/logout
quarkus.oidc.logout.post-logout-redirect-path=/

# Session
quarkus.http.auth.session.cookie.enabled=true
quarkus.http.auth.session.cookie.name=OPEN_PACE_SESSION

# Security permissions
quarkus.http.auth.permission.authenticated.paths=/api/auth/me,/api/users/*/outbox
quarkus.http.auth.permission.authenticated.policy=authenticated
```

### application.properties (test)
```properties
quarkus.oidc.enabled=false
quarkus.security.users.embedded.plain-text=true
quarkus.security.users.embedded.enabled=true
quarkus.security.users.embedded.users.testuser=testuser
quarkus.security.users.embedded.roles.testuser=user
quarkus.http.auth.basic=true
```

## Testing

Tests use an embedded user provider (elytron-security-properties-file) with a pre-seeded test user:
- Username: `testuser`
- Password: `testuser`
- Role: `user`

The test user is created in `src/test/resources/import.sql` with matching entries in both `users` and `actors` tables.

OIDC is disabled in tests (`quarkus.oidc.enabled=false`) so tests verify business logic without requiring an external OIDC server.

## Migration from Basic Auth

**What changed:**
- Removed `quarkus-security-jpa` dependency
- Added `quarkus-oidc` + `quarkus-oidc-client` dependencies
- Removed `password` column from `users` table
- Added `display_name` column to `users` table
- Created `external_identity` table
- Removed `@UserDefinition`, `@Username`, `@Password`, `@Roles` annotations from User entity
- `AuthResource` now uses `@Authenticated` instead of `@RolesAllowed("user")`
- User creation is now automatic via `OidcAuthObserver` on first OIDC login

**Breaking changes:**
- `POST /api/auth/register` endpoint removed (users are created via OIDC)
- `auth().basic(username, password)` no longer works for programmatic API access
- All protected endpoints now require OIDC session tokens
