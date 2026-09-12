# SQL-only user storage

User storage now supports `SQLITE` and `MYSQL` only. `FLAT` is removed from the
enum, normal reads/writes, enumeration, column operations, startup, and deprecated
per-player file helpers. Existing `Data/*.yml` files are neither deleted nor
converted by this change. Ordinary YAML configuration and generic file utilities
remain available.

An explicit `DataStorage: FLAT` setting fails with an explanatory error instead
of silently starting with an empty SQL database. Complete conversion with the
existing conversion commands in the previous version before upgrading, then
select the populated `SQLITE` or `MYSQL` backend. No new migration, automatic
storage switch, schema change, or rewrite of the existing SQL converter is added.
SQL-to-SQL conversion remains available through `convertDataStorage`.

This deliberately removes the deprecated `UserStorage.FLAT` enum constant,
`UserData.getData(String)`, and the per-player file CRUD methods on
`FileThread.ReadThread`. Integrations still calling those FLAT-only APIs must
migrate before upgrading. SQL-facing public APIs, fetch modes, cached changes,
write scheduling, serialized values, and reward queues are unchanged.

The follow-up SQL abstraction builds on this removal. Coordinate both branches
with changes to user data/cache code in PR #317; this removal does not merge or
modify that PR. SimpleAPI remains `1.0.2-SNAPSHOT` in the single Maven project.
