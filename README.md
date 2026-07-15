# Auth API

Authentication and user-management API for Coding4World applications.

This project will centralize users, refresh tokens, JWT issuing, and public key exposure for applications such as `bdi-api`.

## Current phase

Phase 7 is complete. `auth-api` now owns authentication/user rate limits while `bdi-api` owns only BDI-specific rate limits:

- login requests are limited per client IP;
- refresh requests are limited per client IP;
- user administration requests are limited per authenticated administrator;
- public JWKS, health, and info endpoints are not rate-limited by these policies;
- BDI current, history, and BDI refresh-job limits remain in `bdi-api`.

Migration tooling will be implemented in a later phase.

## Requirements

- JDK 21
- Maven Wrapper included in this repository
- Docker and Docker Compose for local MongoDB

## Run tests

```bash
./mvnw -Dkotlin.compiler.daemon=false clean verify
```

## Validate Docker Compose

```bash
docker compose config
```

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

The API will be available at:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/swagger-ui.html`

## Bootstrap administrator

The first administrator can be created at startup by setting both bootstrap variables:

```bash
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=strong-password
```

The bootstrap password must contain at least 12 characters. The bootstrap process only creates an administrator when no existing user has the `ADMIN` role.

## Authentication endpoints

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "strong-password",
  "audience": "bdi-api"
}
```

### Refresh

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "opaque-refresh-token",
  "audience": "bdi-api"
}
```

### Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer access-token
Content-Type: application/json

{
  "refreshToken": "opaque-refresh-token"
}
```

### JWKS

```http
GET /api/v1/auth/jwks
```

The JWKS response exposes only public RSA signing key material. Resource servers should use this endpoint to validate JWT signatures issued by `auth-api`.

## User administration endpoints

All user administration endpoints require an authenticated access token with the `ADMIN` role.

```http
POST /api/v1/admin/users
Authorization: Bearer admin-access-token
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password",
  "roles": ["USER"],
  "enabled": true
}
```

```http
GET /api/v1/admin/users?page=0&size=20
Authorization: Bearer admin-access-token
```

```http
GET /api/v1/admin/users/{userId}
Authorization: Bearer admin-access-token
```

```http
PATCH /api/v1/admin/users/{userId}
Authorization: Bearer admin-access-token
Content-Type: application/json

{
  "roles": ["USER", "ADMIN"],
  "enabled": true
}
```

User responses never expose password hashes.

## Rate limiting

| Operation | Default limit | Bucket identity |
| --- | --- | --- |
| Login | 5 requests/minute | Client IP |
| Refresh | 10 requests/minute | Client IP |
| User administration | 5 requests/hour | Authenticated administrator |

Rate-limited responses return `429 Too Many Requests`, `Retry-After`, `RateLimit-*`, `X-RateLimit-*`, and Problem Details with code `RATE_LIMIT_EXCEEDED`.

`AUTH_API_RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` makes public login/refresh buckets use forwarded IP headers. Enable it only when `auth-api` is behind a trusted reverse proxy that sanitizes those headers.

## Configuration

Important environment variables:

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | MongoDB connection string |
| `JWT_ISSUER` | Issuer used in access tokens |
| `JWT_ALLOWED_AUDIENCES` | Comma-separated list of allowed token audiences |
| `JWT_PUBLIC_KEY` | RSA public key for production JWT signing |
| `JWT_PRIVATE_KEY` | RSA private key for production JWT signing |
| `BOOTSTRAP_ADMIN_EMAIL` | Initial administrator email placeholder |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial administrator password placeholder |
| `AUTH_API_RATE_LIMIT_TRUST_FORWARDED_HEADERS` | Use forwarded IP headers for public auth rate-limit buckets; enable only behind a trusted proxy |

`auth-api` must not define `bdi-api` as a hardcoded default audience. Consumers must request an explicit configured audience.
