-- Harden default privileges: objects created in public from now on are not accessible to anon.
-- Only affects future objects; existing grants (including anon EXECUTE on the 3 immutable helpers) are unchanged.
-- Migrations run as postgres, which owns every public object we create. The supabase_admin defaults
-- (also present in pg_default_acl) are managed by the platform and are not touched here.
alter default privileges for role postgres in schema public revoke all on tables from anon;
alter default privileges for role postgres in schema public revoke all on functions from anon;
alter default privileges for role postgres in schema public revoke all on sequences from anon;
-- Postgres also grants EXECUTE on new functions to PUBLIC (which includes anon). That default is global,
-- so it can only be dropped without "in schema". authenticated and service_role keep their explicit
-- per-schema default EXECUTE grants in public. Side effect (cannot be scoped): functions created by
-- postgres in other schemas also lose implicit PUBLIC execute and need an explicit grant.
alter default privileges for role postgres revoke execute on functions from public;
