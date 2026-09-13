# Shared SQL user access

This follows removal of FLAT user storage. `core.user.storage.SqlUserStorage`
is a game-independent, per-user SQL access contract, and `SqlUserDataAccess`
implements row interpretation and typed reads/writes against it. A native host
can supply a storage implementation without an `AdvancedCorePlugin` instance.

`BukkitSqlUserStorage` reuses the existing MySQL and SQLite user-table providers.
It resolves the plugin, provider, and UUID lazily at operation time. It neither
constructs connections nor owns a cache, executor, identity resolver, schema, or
shutdown lifecycle. Shared access performs no game-thread dispatch. SQL calls
may block and must remain on the caller's appropriate storage execution context.

The headless `SqliteUserBackend` deliberately does not shade another SQLite driver
into AdvancedCore. Bukkit/Paper/Folia deployments use the server runtime's
`org.sqlite.JDBC`; a future Fabric/Forge/NeoForge host must provide `sqlite-jdbc`
as a loader/runtime dependency (or download it in that loader's dependency phase)
before constructing the backend. The test-scoped Maven dependency exists only so
headless persistence tests can run and must not be interpreted as runtime bundling.

The existing `UserData` facade delegates its SQL operations to this access layer.
Its temporary-cache field and accessors, six fetch modes, user-cache precedence,
cache updates, notifications, list encoding, and synchronous/asynchronous write
scheduling stay in place. Row reads still dispatch through the virtual
`getMySqlRow()` / `getSQLiteRow()` methods, and `getValues` still calls the virtual
`convert` method. Reload/replacement does not leave the adapter holding old tables
or an eagerly captured UUID. No new cache or queue is introduced.

The port deliberately preserves existing provider behavior, including the
MySQL missing-provider bulk-write no-op and SQLite cumulative per-entry bulk
updates. It does not claim that void-returning provider methods establish durable
completion, atomic batches, or successful disk commits. Async reward completion
and checkpoint work in PR #317 remain separate; do not substitute this interface
for that work's completion contract.

## Scope and validation

This is the SQL-access boundary, not the complete native user runtime. Existing
Bukkit-facing provider construction, SQLite connection/table bootstrap, schema
registration, `UserManager` enumeration, and user-cache lifecycle still need
native wiring. The current full AdvancedCore JAR is not a Fabric/Forge/NeoForge
artifact. No alternate database implementation or duplicate JDBC stack is added.

Tests cover typed/default/case behavior, live rows, subclass hooks, map identity,
backend delegation, bulk call ordering, lazy ownership, all existing fetch modes,
queued writes, asynchronous identity resolution, deletion/rejection failures, and
isolated shared-class loading. The headless fixture uses an in-memory port; it is
not a database integration test. Preserve existing SQL tests as well.

One Maven project, existing package layout, SimpleAPI `1.0.2-SNAPSHOT`, no new
workflow or dependency pin. Merge the FLAT-removal prerequisite first, reconcile
#317's overlapping user/cache work, and validate the exact candidate through
AdvancedCore and VotingPlugin builds plus packaged database/server tests.
