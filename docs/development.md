# Local development database

The application uses Flyway migrations from `src/main/resources/db/migrations` and validates the resulting schema with Hibernate. Start with an empty MySQL database; the first migration creates the schema.

To add fictional programme-catalogue data, activate the `dev` Spring profile. It inserts a demo academic year and three programme levels only when they are missing. It does not create accounts, passwords, applicants, or identity documents.
