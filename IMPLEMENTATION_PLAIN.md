# Auth API Extraction Implementation Plan

## Project definition

- Repository and application name: `auth-api`
- Location: `applications/auth-api`
- Maven coordinates: `com.coding4world:auth-api`
- Kotlin root package: `com.coding4world.auth.api`
- Language: English for source code, comments, API messages, documentation, and logs
- Runtime: JDK 21
- Framework: Spring Boot 4.1 with Kotlin and Spring MVC
- Database: MongoDB
- API versioning: URI-based versioning under `/api/v1`

## Architecture

The new `auth-api` will own authentication, users, refresh tokens, JWT issuing, and public key exposure.

Primary features:

- `auth`: login, token refresh, logout, current authenticated user, and JWT issuing
- `user`: administrator-managed user accounts and roles
- `shared`: configuration, error handling, observability, rate limiting, and security infrastructure

After extraction, `bdi-api` will no longer issue tokens or manage users. It will become a Resource Server that validates JWTs issued by `auth-api`.

## REST API

### `auth-api`

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Public | Authenticate with email and password |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate a refresh token and issue a new access token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revoke a refresh token |
| `GET` | `/api/v1/auth/me` | Authenticated | Return authenticated user summary |
| `GET` | `/api/v1/auth/jwks` | Public | Expose public signing keys |
| `POST` | `/api/v1/admin/users` | `ADMIN` | Create a user |
| `GET` | `/api/v1/admin/users` | `ADMIN` | List users |
| `GET` | `/api/v1/admin/users/{id}` | `ADMIN` | Get user details |
| `PATCH` | `/api/v1/admin/users/{id}` | `ADMIN` | Change roles or enable/disable a user |

Login and refresh requests must explicitly include `audience`.

```json
{
  "email": "admin@example.com",
  "password": "strong-password",
  "audience": "bdi-api"
}
```

If `audience` is missing or not configured as allowed, the API returns `400 Bad Request` with Problem Details.

JWT claims will include:

- `iss`
- `aud`
- `sub`
- `jti`
- `roles`
- `iat`
- `exp`

## Changes to `bdi-api`

Remove from `bdi-api`:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{id}`
- user persistence
- refresh token persistence
- JWT private key configuration
- administrator bootstrap

Keep in `bdi-api`:

- BDI current endpoint
- BDI history endpoint
- BDI refresh job endpoints
- role-based authorization
- rate limiting for BDI and admin operations

Configure `bdi-api` to validate external JWTs using:

```yaml
bdi-api:
  security:
    jwt:
      issuer: ${AUTH_JWT_ISSUER}
      audience: bdi-api
      jwks-uri: ${AUTH_JWKS_URI}
```

## Persistence

`auth-api` will own these MongoDB collections:

- `users`: normalized email, password hash, roles, enabled state, and audit timestamps
- `refresh_tokens`: token hash, token family, expiration, revocation state, and replacement hash

`bdi-api` will keep only BDI-related collections.

Existing users will be copied from `bdi-api.users` to `auth-api.users`. Existing refresh tokens will not be migrated, so users must log in again.

## Authentication and authorization

- `auth-api` signs RS256 JWT access tokens.
- `auth-api` stores only refresh token hashes.
- Refresh tokens remain opaque, single-use, rotated on every refresh, and valid for 7 days.
- Access tokens remain valid for 15 minutes.
- Private signing keys exist only in `auth-api`.
- `bdi-api` validates tokens using issuer, audience, and JWKS/public key.
- Roles remain global for the first version: `USER`, `ADMIN`.

## Rate limiting

`auth-api` will own:

| Operation | Limit | Key |
| --- | --- | --- |
| Login | 5 requests/minute | Client IP |
| Token refresh | 10 requests/minute | Client IP |
| User administration | 5 requests/hour | Authenticated administrator |

`bdi-api` will keep:

| Operation | Limit | Key |
| --- | --- | --- |
| Current BDI | 60 requests/minute | Authenticated user |
| BDI history | 30 requests/minute | Authenticated user |
| BDI admin refresh | 5 requests/hour | Authenticated administrator |

## Delivery schedule

The estimate assumes one developer and covers ten working days.

Implementation progress:

- [ ] Contract and bootstrap
- [ ] Authentication extraction
- [ ] User management extraction
- [ ] JWKS and token validation
- [ ] BDI API cleanup
- [ ] User migration
- [ ] Integration testing
- [ ] Delivery tooling
- [ ] Hardening and release

| Step | Stage | Deliverables | Completion gate |
| --- | --- | --- | --- |
| 1 | Contract and bootstrap | Create `auth-api` Maven project, package structure, profiles, configuration properties, Docker Compose, README skeleton, and CI workflow | `auth-api` starts locally with test profile |
| 2 | Authentication extraction | Move/adapt login, refresh, logout, JWT issuing, refresh token model, refresh token repository, and token rotation rules | Authentication service tests pass in `auth-api` |
| 3 | User management extraction | Move/adapt user model, user repository, administrator bootstrap, create/update endpoints, and add list/get endpoints | User management contract tests pass |
| 4 | Explicit audience support | Add required `audience` to login/refresh requests, allowed audience configuration, and validation errors | Tokens are issued only for configured audiences |
| 5 | JWKS/public key support | Expose public signing keys from `auth-api`; keep private key restricted to `auth-api` | Resource-server test can validate token using JWKS |
| 6 | BDI API resource-server migration | Remove auth/user endpoints from `bdi-api`; remove user/refresh persistence; configure external issuer/audience/JWKS validation | `bdi-api` accepts valid `auth-api` token and rejects wrong audience |
| 7 | Rate limiting split | Move login/refresh rate limits to `auth-api`; keep BDI/admin limits in `bdi-api` | Rate-limit tests pass in both projects |
| 8 | User migration tooling | Add documented migration script or command to copy users from `bdi-api` MongoDB to `auth-api` MongoDB | Migrated user can log in through `auth-api` with existing password |
| 9 | Integration testing | Add Docker Compose or documented local flow for `auth-api` + `bdi-api` + MongoDB | End-to-end login, refresh, and protected BDI request pass |
| 10 | Hardening and release | Security review, documentation, changelog, release checklist, secret review, and final verification | `mvn verify` passes in both projects |

## Test strategy

### `auth-api`

- Login succeeds for enabled user with valid password.
- Login fails for unknown, disabled, or invalid-password user.
- Login fails when `audience` is missing.
- Login fails when `audience` is not allowed.
- Access token contains expected issuer, audience, subject, roles, issued-at, expiration, and JWT id.
- Refresh token rotation issues a new token pair.
- Reusing a revoked refresh token revokes the token family.
- Logout revokes the supplied refresh token.
- Admin can create, list, get, and update users.
- Non-admin cannot manage users.
- JWKS endpoint exposes public key material only.
- MongoDB indexes enforce unique normalized email and refresh token hash.
- Rate limits return `429`, `Retry-After`, and rate-limit headers.

### `bdi-api`

- Missing token returns `401`.
- Token with wrong issuer returns `401`.
- Token with wrong audience returns `401`.
- Token with `USER` role can access BDI current/history.
- Token with `USER` role cannot access admin refresh.
- Token with `ADMIN` role can access admin refresh.
- Removed auth/user endpoints are absent.
- Existing BDI behavior remains unchanged.

### Integration

- Start `auth-api`, `bdi-api`, and MongoDB.
- Bootstrap admin in `auth-api`.
- Login through `auth-api` with `audience: "bdi-api"`.
- Call protected `bdi-api` endpoint with returned access token.
- Refresh through `auth-api`.
- Call `bdi-api` again with the new access token.
- Confirm `bdi-api` rejects a token for another audience.

## Acceptance criteria

- `auth-api` is the only service storing users, passwords, refresh tokens, and private signing keys.
- `bdi-api` no longer contains authentication issuing or user-management behavior.
- Access tokens require an explicit configured audience.
- No BDI-specific default audience exists in `auth-api`.
- `bdi-api` accepts only tokens issued by `auth-api` for audience `bdi-api`.
- Existing users can be copied to `auth-api` without password reset.
- Existing refresh tokens are intentionally invalidated.
- Both projects pass their Maven verification pipelines.
- Documentation explains local startup, production secrets, migration, and service interaction.

## Assumptions

- First version uses the current custom token API, not full OAuth2/OIDC.
- Roles remain global: `USER` and `ADMIN`.
- Public registration is out of scope.
- Password reset, email verification, MFA, groups, and per-application permissions are deferred.
- A single application instance is acceptable initially.
- Distributed rate limiting can be added later.
