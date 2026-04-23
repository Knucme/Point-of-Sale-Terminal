-- Enums are now stored as VARCHAR by Hibernate (no native PostgreSQL enum types needed).
-- This avoids the Hibernate 6.4 ddl-auto=update incompatibility with NAMED_ENUM columns.
SELECT 1;
