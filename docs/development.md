# Local development database

See the [developer installation guide](developer-install.md) for JDK, MySQL, secrets, startup, and test instructions.

The application uses Flyway migrations from `src/main/resources/db/migrations` and validates the resulting schema with Hibernate. Start with an empty MySQL database; the first migration creates the schema.

To add fictional programme-catalogue data, activate the `dev` Spring profile. It inserts a demo academic year and missing S1-S6 levels, with the prescribed IB MYP/DP mapping, 150 seats per new level and S6 closed by default. If another year is active, the new demo year stays in planning. Existing settings and archived years are not changed. It does not create accounts, passwords, applicants, or identity documents. Do not activate this profile in production.
