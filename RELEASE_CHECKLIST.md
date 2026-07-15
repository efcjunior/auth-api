# Release checklist

Use this checklist before publishing `v1.0.0`.

## Source and dependency checks

- [ ] `main` is up to date and contains only reviewed changes.
- [ ] The GitHub Actions `CI` workflow passes on the release commit.
- [ ] `./mvnw -Dkotlin.compiler.daemon=false clean verify` passes locally.
- [ ] `docker compose config` succeeds.
- [ ] `docker compose build api` succeeds after the JAR is built.

## Security checks

- [ ] No passwords, JWTs, refresh tokens, private keys, or PATs are committed.
- [ ] Any token accidentally shared during development has been revoked.
- [ ] Production signing keys are configured from deployment secrets only.
- [ ] Bootstrap administrator credentials are used only for first startup and removed after the first administrator account exists.
- [ ] CORS remains disabled unless explicit trusted origins are configured.
- [ ] Forwarded IP headers are trusted only behind a controlled reverse proxy.
- [ ] Actuator health details remain sanitized in production.

## Acceptance checks

- [ ] Protected endpoints reject missing, invalid, expired, or unauthorized tokens.
- [ ] Refresh token rotation and reuse detection work.
- [ ] JWKS exposes public key material only.
- [ ] User administration endpoints require `ADMIN`.
- [ ] Rate-limited requests return `429`, `Retry-After`, and rate-limit headers.
- [ ] Local startup is reproducible with Docker Compose.

## Release steps

- [ ] Update this checklist with the final verification result.
- [ ] Create release tag `v1.0.0`.
- [ ] Publish GitHub Release notes from `CHANGELOG.md`.
- [ ] Decide whether to publish a Docker image or Maven artifact for `auth-api`.
