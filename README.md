# Auth API

Authentication and user-management API for Coding4World applications.

This project will centralize users, refresh tokens, JWT issuing, and public key exposure for applications such as `bdi-api`.

## Current phase

Phase 1 implements only the technical bootstrap:

- Spring Boot/Kotlin project structure;
- configuration properties;
- health/info actuator endpoints;
- base security configuration;
- base Problem Details handling;
- Docker Compose and CI workflow.

Login, refresh tokens, user management, and JWKS will be implemented in later phases.

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

## Configuration

Important environment variables:

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | MongoDB connection string |
| `JWT_ISSUER` | Issuer to use when JWT issuing is implemented |
| `JWT_ALLOWED_AUDIENCES` | Comma-separated list of allowed token audiences |
| `JWT_PUBLIC_KEY` | Public key placeholder for later JWT support |
| `JWT_PRIVATE_KEY` | Private key placeholder for later JWT support |
| `BOOTSTRAP_ADMIN_EMAIL` | Initial administrator email placeholder |
| `BOOTSTRAP_ADMIN_PASSWORD` | Initial administrator password placeholder |

`auth-api` must not define `bdi-api` as a hardcoded default audience. Consumers must request an explicit configured audience.
