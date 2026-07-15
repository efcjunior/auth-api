# Security policy

## Supported versions

The first supported release will be `v1.0.0`.

## Reporting a vulnerability

Do not open a public issue for security reports. Contact the repository owner privately and include:

- affected endpoint or component;
- reproduction steps;
- expected and observed behavior;
- potential impact;
- relevant logs with secrets removed.

## Secret handling

Never commit or paste:

- JWT private keys;
- GitHub personal access tokens;
- passwords;
- access tokens;
- refresh tokens;
- production `.env` files.

If a secret is exposed, revoke it immediately, create a replacement, and update the affected GitHub Actions or deployment secret.

## Production requirements

- Run without the `local` or `test` profile.
- Configure JWT signing keys from a secret store.
- Remove bootstrap administrator credentials after the first administrator account exists.
- Keep Actuator health details sanitized.
- Enable forwarded IP header trust only behind a controlled reverse proxy.
- Use a shared rate-limit store before horizontal scaling.
