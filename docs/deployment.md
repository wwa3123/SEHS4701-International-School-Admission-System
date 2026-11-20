# Deployment guide

This guide describes a production-oriented deployment of the Spring Boot application as an executable JAR with MySQL. It is platform-neutral; adapt the service manager, secret store, TLS proxy, and backup procedures to your hosting provider.

## Before deployment

- Use a supported JDK 25 runtime and MySQL 8.0.16 or later.
- Create a dedicated production database and application account. Grant only the database privileges Flyway needs to create and update schema objects.
- Configure a TLS-terminating reverse proxy or load balancer. Expose the application only through HTTPS, restrict direct access to its application port, and ensure the proxy overwrites forwarded headers.
- Store credentials and keys in the host's secret manager. Do not commit them, put them in the JAR, or reuse development values.
- Do not activate the `dev` profile or ship local development configuration. The dev profile creates fictional admission catalogue data.
- Ensure `src\main\resources\application-dev.properties` is absent from the production build input: Git ignores it, but Maven can still package it, including any local secrets.

## Required configuration

Set these environment variables (or equivalent Spring Boot properties through your deployment platform):

| Environment variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | Production MySQL JDBC URL. Enable TLS certificate verification; do not copy the local `useSSL=false` URL. |
| `SPRING_DATASOURCE_USERNAME` | Dedicated application database account. |
| `SPRING_DATASOURCE_PASSWORD` | Database credential. |
| `IDENTITY_DOCUMENT_SECRET` | Stable, high-entropy secret used to encrypt identity-document values. |
| `IDENTITY_DOCUMENT_SALT` | Stable salt paired with the encryption secret. |
| `IDENTITY_DOCUMENT_HASH_PEPPER` | Stable secret pepper for identity matching hashes. |
| `OTC_CODE_HASH_PEPPER` | Stable secret pepper for one-time-code hashes. |
| `CISSAS_SUPER_ADMIN_EMAIL` | Initial super-administrator email, used only if no super administrator exists. |
| `CISSAS_SUPER_ADMIN_FIRST_NAME` | Initial super-administrator first name. |
| `CISSAS_SUPER_ADMIN_LAST_NAME` | Initial super-administrator last name. |
| `CISSAS_SUPER_ADMIN_PASSWORD` | Initial super-administrator password (at least 8 characters and no more than 72 UTF-8 bytes). |
| `SPRING_MAIL_HOST` | SMTP host used to deliver notifications. |
| `SPRING_MAIL_PORT` | SMTP port. |
| `SPRING_MAIL_USERNAME` | SMTP account. |
| `SPRING_MAIL_PASSWORD` | SMTP credential. |

Keep encryption and hash keys stable across deployments and backups. Losing or rotating the identity encryption secret/salt makes existing encrypted documents unreadable; changing hash peppers can prevent existing identity/code hashes from matching. Use the same values for every application instance connected to the same database.

Configure `CISSAS_ADMIN_NOTIFICATION_EMAIL` if duplicate-review alerts should also be sent to a shared administrator inbox. Configure `CISSAS_REGISTRATION_TRUSTED_PROXIES` only with the exact proxy IPs that overwrite `X-Forwarded-For`; do not trust arbitrary client-provided forwarded headers.

Production overrides should include:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false
```

Use the correct production profile name if your deployment supplies a dedicated one. Do not include `dev`.

## Build and start

Build and run tests in CI or on a trusted build machine:

```sh
./mvnw clean verify
```

The executable JAR is created under `target/`. Deploy the exact tested artifact, then start it with the configured environment:

```sh
java -jar target/cissas-0.0.1-SNAPSHOT.jar
```

On first startup, Flyway applies versioned migrations from `src/main/resources/db/migrations`; Hibernate validates the schema. The application provisions the initial super administrator if no super administrator exists. After verifying first-time access, remove the bootstrap password from the deployment environment/secret injection if it is no longer needed. Provisioning does not replace an existing super administrator.

## Operations and upgrades

- Take and verify a restorable database backup before deploying a schema-changing release.
- Apply releases to a staging database first and verify Flyway plus Hibernate validation before production.
- Add schema changes as new sequential migrations. Never modify a migration already applied to a shared or production database.
- Deploy the application artifact and its migration together; review migration compatibility with the rollout strategy, especially for deployments that temporarily run multiple application versions.
- Monitor application startup, database connectivity, SMTP delivery failures, and server logs. The application port should not be directly internet-accessible.
- Test database restore and key recovery procedures periodically. A database backup without its matching encryption and hash secrets may not be usable.

## Super-administrator recovery

Recovery is a maintenance operation, not a normal startup mode. Supply `CISSAS_RECOVERY_EMAIL` and `CISSAS_RECOVERY_PASSWORD` through a protected one-time environment/secret injection, then run the JAR with `--recover-super-admin`. The supplied email must belong to an existing active super administrator. Remove the recovery secrets immediately after the operation and verify the account can sign in.
