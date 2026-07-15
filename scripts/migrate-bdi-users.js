#!/usr/bin/env mongosh
/*
 * Copy legacy bdi-api users into auth-api.
 *
 * This script intentionally does not migrate refresh_tokens. Existing users must
 * log in again through auth-api after migration.
 */

const VALID_ROLES = new Set(['USER', 'ADMIN']);

function env(name, defaultValue = '') {
  const value = process.env[name];
  return value === undefined || value === null || value === '' ? defaultValue : value;
}

function requiredEnv(name) {
  const value = env(name);
  if (!value) {
    fail(`Missing required environment variable ${name}`);
  }
  return value;
}

function booleanEnv(name, defaultValue) {
  const value = env(name, String(defaultValue)).toLowerCase();
  return ['1', 'true', 'yes', 'y'].includes(value);
}

function databaseNameFromUri(uri, fallback) {
  const withoutQuery = uri.split('?')[0];
  const lastSlash = withoutQuery.lastIndexOf('/');
  if (lastSlash < 0 || lastSlash === withoutQuery.length - 1) {
    return fallback;
  }
  const candidate = decodeURIComponent(withoutQuery.substring(lastSlash + 1));
  if (!candidate || candidate.includes('@')) {
    return fallback;
  }
  return candidate;
}

function fail(message) {
  print(`[migrate-bdi-users] ERROR: ${message}`);
  quit(1);
}

function normalizeEmail(value) {
  if (typeof value !== 'string') return '';
  return value.trim().toLowerCase();
}

function normalizeRoles(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((role) => String(role).trim().toUpperCase())
    .filter((role) => role.length > 0);
}

function validateSourceUser(document) {
  const normalizedEmail = normalizeEmail(document.normalizedEmail || document.email);
  const passwordHash = typeof document.passwordHash === 'string' ? document.passwordHash : '';
  const roles = normalizeRoles(document.roles);
  const enabled = document.enabled;
  const errors = [];

  if (!normalizedEmail) errors.push('normalizedEmail is required');
  if (!passwordHash) errors.push('passwordHash is required');
  if (!Array.isArray(document.roles) || roles.length === 0) errors.push('roles must contain at least one role');
  if (roles.some((role) => !VALID_ROLES.has(role))) errors.push(`roles contain unsupported value(s): ${roles.join(',')}`);
  if (typeof enabled !== 'boolean') errors.push('enabled must be boolean');

  return {
    errors,
    migrated: {
      _id: document._id,
      normalizedEmail,
      passwordHash,
      roles,
      enabled,
      createdAt: document.createdAt,
      updatedAt: document.updatedAt,
    },
  };
}

const sourceUri = requiredEnv('BDI_MONGODB_URI');
const targetUri = requiredEnv('AUTH_MONGODB_URI');
const sourceDatabaseName = env('BDI_MONGODB_DATABASE', databaseNameFromUri(sourceUri, 'bdi-api'));
const targetDatabaseName = env('AUTH_MONGODB_DATABASE', databaseNameFromUri(targetUri, 'auth-api'));
const sourceCollectionName = env('BDI_USERS_COLLECTION', 'users');
const targetCollectionName = env('AUTH_USERS_COLLECTION', 'users');
const dryRun = booleanEnv('MIGRATE_USERS_DRY_RUN', true);
const failOnInvalid = booleanEnv('MIGRATE_USERS_FAIL_ON_INVALID', true);

print('[migrate-bdi-users] Starting user migration');
print(`[migrate-bdi-users] Source: ${sourceDatabaseName}.${sourceCollectionName}`);
print(`[migrate-bdi-users] Target: ${targetDatabaseName}.${targetCollectionName}`);
print(`[migrate-bdi-users] Dry run: ${dryRun}`);

const source = new Mongo(sourceUri).getDB(sourceDatabaseName);
const target = new Mongo(targetUri).getDB(targetDatabaseName);
const sourceUsers = source.getCollection(sourceCollectionName);
const targetUsers = target.getCollection(targetCollectionName);

const users = sourceUsers.find({}).sort({ normalizedEmail: 1 }).toArray();
if (users.length === 0) {
  print('[migrate-bdi-users] Source collection is empty. Nothing to migrate.');
  quit(0);
}

let scanned = 0;
let migrated = 0;
let skippedExisting = 0;
let invalid = 0;

if (!dryRun) {
  targetUsers.createIndex({ normalizedEmail: 1 }, { unique: true, name: 'uk_users_normalized_email' });
}

for (const user of users) {
  scanned += 1;
  const validation = validateSourceUser(user);
  if (validation.errors.length > 0) {
    invalid += 1;
    print(`[migrate-bdi-users] Invalid source user ${user._id}: ${validation.errors.join('; ')}`);
    if (failOnInvalid) {
      fail('Invalid source user found. Re-run with MIGRATE_USERS_FAIL_ON_INVALID=false to skip invalid documents.');
    }
    continue;
  }

  const migratedUser = validation.migrated;
  const existing = targetUsers.findOne({
    $or: [
      { _id: migratedUser._id },
      { normalizedEmail: migratedUser.normalizedEmail },
    ],
  });

  if (existing) {
    skippedExisting += 1;
    print(`[migrate-bdi-users] Skipping existing user ${migratedUser.normalizedEmail}`);
    continue;
  }

  migrated += 1;
  if (dryRun) {
    print(`[migrate-bdi-users] Would migrate ${migratedUser.normalizedEmail}`);
  } else {
    targetUsers.insertOne(migratedUser);
    print(`[migrate-bdi-users] Migrated ${migratedUser.normalizedEmail}`);
  }
}

print('[migrate-bdi-users] Summary');
print(`[migrate-bdi-users] scanned=${scanned}`);
print(`[migrate-bdi-users] migrated=${migrated}`);
print(`[migrate-bdi-users] skippedExisting=${skippedExisting}`);
print(`[migrate-bdi-users] invalid=${invalid}`);

if (dryRun) {
  print('[migrate-bdi-users] Dry run only. Set MIGRATE_USERS_DRY_RUN=false to write to auth-api.');
}
