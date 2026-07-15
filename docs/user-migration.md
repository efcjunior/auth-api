# User migration

This migration copies legacy `bdi-api.users` documents into `auth-api.users`.

The current project has not been run yet, so in the expected local case the source collection is empty and the migration is a safe no-op. Run the dry-run command below to confirm that before starting real usage.

## What is migrated

The old `bdi-api` user document has the same relevant fields now owned by `auth-api`:

- `_id`
- `normalizedEmail`
- `passwordHash`
- `roles`
- `enabled`
- `createdAt`
- `updatedAt`

Password hashes are preserved, so migrated users can keep using their existing password.

## What is not migrated

Refresh tokens are intentionally not copied. Users must log in again through `auth-api` after migration.

## Dry run

From the `auth-api` repository root:

```bash
BDI_MONGODB_URI=mongodb://localhost:27017/bdi-api \
AUTH_MONGODB_URI=mongodb://localhost:27017/auth-api \
mongosh --quiet scripts/migrate-bdi-users.js
```

Dry-run mode is enabled by default. If the project never ran, the expected result is:

```text
[migrate-bdi-users] Source collection is empty. Nothing to migrate.
```

In that case no migration is needed. Start `auth-api` normally and create the first administrator with bootstrap configuration or the admin API flow.

## Execute migration

Only run this when the dry run shows users that should be copied:

```bash
BDI_MONGODB_URI=mongodb://localhost:27017/bdi-api \
AUTH_MONGODB_URI=mongodb://localhost:27017/auth-api \
MIGRATE_USERS_DRY_RUN=false \
mongosh --quiet scripts/migrate-bdi-users.js
```

The script creates the `uk_users_normalized_email` index in the target database before inserting users.

## Docker alternative

If `mongosh` is not installed locally, use the MongoDB image:

```bash
docker run --rm --network host \
  -v "$PWD/scripts:/scripts:ro" \
  -e BDI_MONGODB_URI=mongodb://localhost:27017/bdi-api \
  -e AUTH_MONGODB_URI=mongodb://localhost:27017/auth-api \
  mongo:8.0 mongosh --quiet /scripts/migrate-bdi-users.js
```

Add `-e MIGRATE_USERS_DRY_RUN=false` only after reviewing the dry-run output.

## Validation after migration

1. Start `auth-api` with the target MongoDB.
2. Log in with a migrated user's email, password, and explicit audience.
3. Confirm the response contains a bearer access token and a new refresh token.

Example:

```bash
curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"existing-password","audience":"bdi-api"}'
```
