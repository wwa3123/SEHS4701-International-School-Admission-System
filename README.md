# CISSAS

CIS International School Admission System (CISSAS) is a web application for managing school admissions, applications, results, offer responses, and duplicate-applicant reviews.

## Features

- Member registration, sign-in, application submission, and application status history.
- Administrator and super-administrator workflows for admissions review, result release, offer deadlines, and duplicate review.
- Offer acceptance or rejection with confirmation and a fixed response deadline.
- Email notifications with tracking and administrative handling for failed deliveries.
- Responsive Thymeleaf pages with locally hosted Bootstrap assets.

The application enforces admissions rules on the server; page controls and guidance are not the security boundary. “Administrator” includes admissions officers (`ROLE_ADMIN`) and super administrators (`ROLE_SUPER_ADMIN`); some corrections and account-management actions are restricted to super administrators.

## Technology

- Java 25 and Spring Boot 4.1
- MySQL with Flyway-managed SQL migrations
- Spring Data JPA, Spring Security, and Thymeleaf
- Bootstrap 5.3.2, served from the application
- Maven wrapper for builds and tests

## Get started

For prerequisites, local MySQL setup, environment variables, and run instructions, follow the [Developer installation guide](docs/developer-install.md).

For production configuration, build, operations, upgrades, and recovery, see the [Deployment guide](docs/deployment.md).

### Commands

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
./mvnw test
./mvnw spring-boot:run
```

The application listens on port `8080` by default.

## Database and demo catalogue

Flyway applies versioned schema migrations from `src/main/resources/db/migrations` at startup. Hibernate is configured to validate the migrated schema; it does not create or update tables. Start a first-time installation with an empty MySQL database.

For local development only, activate the `dev` Spring profile to seed a fictional academic year and three programme levels. The seed is idempotent and creates no user accounts, applicants, or identity documents. Do not enable this profile in production.

For assessment or local development, copy the supplied `src\main\resources\application-dev.properties.example` to `application-dev.properties` in the same directory. The copy is Git-ignored and uses the local settings described in the [Developer installation guide](docs/developer-install.md); it contains no working credentials by default.

## Project layout

```text
src/main/java/     Application, controllers, services, repositories, and entities
src/main/resources/
  db/migrations/   Flyway schema migrations
  static/          CSS and locally hosted vendor assets
  templates/       Thymeleaf pages
src/test/java/     Automated tests
docs/              Developer installation and deployment guides
```

## Configuration and sensitive data

The application needs a MySQL connection, identity-document encryption settings, and super-administrator bootstrap details on a fresh database. Configure secrets through environment variables or a secret manager; do not commit credentials or local configuration. Encryption keys and hash peppers must remain stable for data already stored with them. Email delivery requires valid SMTP settings.

See the [Developer installation guide](docs/developer-install.md) for the local configuration variables and the [Deployment guide](docs/deployment.md) for production requirements and operational precautions.

## Contributing database changes

Add a new sequential Flyway migration under `src/main/resources/db/migrations` for each schema change. Do not edit migrations that have already been applied to shared databases. Test migrations and Hibernate schema validation against a disposable MySQL database before sharing a change.
