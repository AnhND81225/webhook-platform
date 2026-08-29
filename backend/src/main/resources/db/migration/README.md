# Flyway migrations

Flyway owns all schema changes. `V1__create_users.sql` introduces the local dashboard user required by M1. `V2__create_applications_and_api_keys.sql` adds the M2 owner-scoped Applications and reveal-once API-key metadata. Hibernate remains configured with `ddl-auto=validate`.
