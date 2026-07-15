# Auth API

Authentication and user-management API for Coding4World applications.

This project will centralize users, refresh tokens, JWT issuing, and public key exposure for applications such as `bdi-api`.

## Current phase

Phase 2 implements authentication extraction:

- login with email, password, and explicit audience;
- signed RS256 JWT access tokens;
- opaque refresh tokens stored only as SHA-256 hashes;
- single-use refresh token rotation;
- logout by refresh token revocation;
- minimal user persistence required for authentication.

User administration endpoints, bootstrap administrator creation, JWKS, rate limiting, and BDI API migration will be implemented in later phases.

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

`auth-api` must not define `bdi-api` as a hardcoded default audience. Consumers must request an explicit configured audience.
