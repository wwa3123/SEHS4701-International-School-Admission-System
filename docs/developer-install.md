# Developer installation guide

This guide covers a fresh local setup. It does not use or require another developer's ignored local configuration files.

## Prerequisites

- JDK 25 (ensure `java -version` uses it)
- MySQL 8.0.16 or later, running locally or reachable from your machine
- Git
- OpenSSL for the random-key commands below

The Maven wrapper downloads the project’s Maven version and dependencies. A separate Maven installation is not required.

## 1. Get the code

```sh
git clone <repository-url>
cd <repository-directory>
```

On Windows PowerShell, use `.\mvnw.cmd` in place of `./mvnw` in the commands below.

## 2. Create a local database

Create an empty database and a dedicated account with permission to create/alter tables and indexes. For example, run as a MySQL administrator and replace the example password with a private local value:

```sql
CREATE DATABASE cissas CHARACTER SET utf8mb4;
CREATE USER 'cissas_dev'@'localhost' IDENTIFIED BY 'replace-with-a-local-password';
GRANT ALL PRIVILEGES ON cissas.* TO 'cissas_dev'@'localhost';
```

The default JDBC URL targets `jdbc:mysql://localhost:3306/cissas`. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` if your connection differs. The first application startup applies Flyway migrations from `src/main/resources/db/migrations`; Hibernate then validates the schema. Do not use `ddl-auto=update` as a replacement for migrations.

## 3. Configure local secrets

The application requires encryption settings and, on a fresh database, super-administrator bootstrap details. Keep these values private; do not commit them or reuse production keys in development.

Generate these values once for a new local database, store them privately, and reuse them in later terminal sessions. Changing the encryption values or peppers while reusing the database can make stored data unreadable or prevent hashes from matching. The examples use OpenSSL to produce hex-encoded random values.

PowerShell:

```powershell
$env:IDENTITY_DOCUMENT_SECRET = (openssl rand -hex 32).Trim()
$env:IDENTITY_DOCUMENT_SALT = (openssl rand -hex 16).Trim()
$env:IDENTITY_DOCUMENT_HASH_PEPPER = (openssl rand -hex 32).Trim()
$env:OTC_CODE_HASH_PEPPER = (openssl rand -hex 32).Trim()

$env:CISSAS_SUPER_ADMIN_EMAIL = 'developer@example.test'
$env:CISSAS_SUPER_ADMIN_FIRST_NAME = 'Local'
$env:CISSAS_SUPER_ADMIN_LAST_NAME = 'Developer'
$env:CISSAS_SUPER_ADMIN_PASSWORD = 'replace-with-a-private-password'

$env:SPRING_DATASOURCE_USERNAME = 'cissas_dev'
$env:SPRING_DATASOURCE_PASSWORD = 'replace-with-a-local-password'
```

macOS/Linux:

```sh
export IDENTITY_DOCUMENT_SECRET="$(openssl rand -hex 32)"
export IDENTITY_DOCUMENT_SALT="$(openssl rand -hex 16)"
export IDENTITY_DOCUMENT_HASH_PEPPER="$(openssl rand -hex 32)"
export OTC_CODE_HASH_PEPPER="$(openssl rand -hex 32)"

export CISSAS_SUPER_ADMIN_EMAIL='developer@example.test'
export CISSAS_SUPER_ADMIN_FIRST_NAME='Local'
export CISSAS_SUPER_ADMIN_LAST_NAME='Developer'
export CISSAS_SUPER_ADMIN_PASSWORD='replace-with-a-private-password'

export SPRING_DATASOURCE_USERNAME='cissas_dev'
export SPRING_DATASOURCE_PASSWORD='replace-with-a-local-password'
```

Choose a bootstrap password of at least 8 characters and no more than 72 UTF-8 bytes. The super administrator is provisioned only when no super-administrator account exists. Keep the same identity encryption secret and salt for any database whose encrypted identity documents must remain readable; keep the hash peppers stable so existing hashes continue to match.

### Create the dev profile file

Copy the supplied example to `src\main\resources\application-dev.properties` before running with the `dev` profile. The file is Git-ignored and is absent from a fresh clone. If you already have your own file, keep it instead of overwriting it.

PowerShell:

```powershell
Copy-Item .\src\main\resources\application-dev.properties.example .\src\main\resources\application-dev.properties
```

macOS/Linux:

```sh
cp src/main/resources/application-dev.properties.example src/main/resources/application-dev.properties
```

The example reads the database credentials, encryption values, peppers, and first super-administrator details from the environment variables above. Spring loads the copied file only with the `dev` profile; its settings override the defaults in `application.properties`. You may instead put your own local values in the ignored copy, but never commit or share credentials. Direct values in this file override the base property's environment-variable placeholders.

Git ignoring the file does **not** prevent Maven from packaging it as a resource. Remove local secret-bearing profile files from the build input before creating a distributable JAR; never ship development credentials.

Mail settings default to a placeholder SMTP host. To test email delivery, configure `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD` with credentials for a development mail service. Without a working SMTP service, email delivery will fail and can be inspected through the application’s email-failure handling.

## 4. Run locally

To include the fictional demo academic year and six programme levels (S1-S6), activate the `dev` profile. New levels have 150 seats, with S6 closed by default. If another year is active, the demo year stays in planning. The seeder preserves existing settings and archived years; it does not create member accounts, applicants, or identity documents.

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
SPRING_PROFILES_ACTIVE=dev SPRING_JPA_HIBERNATE_DDL_AUTO=validate ./mvnw spring-boot:run
```

The application is available at <http://localhost:8080>. Sign in with the super-administrator email and password configured above.

Do not activate the `dev` profile in production.

## 5. Run tests

Windows:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```sh
./mvnw test
```

## Database migration workflow

Add schema changes as a new sequentially numbered Flyway SQL migration in `src/main/resources/db/migrations` (for example, `V2__add_example_column.sql`). Do not edit a migration that has already been applied to a shared database; add a new migration instead. Start the application against a disposable MySQL database to verify migrations and Hibernate schema validation together.
