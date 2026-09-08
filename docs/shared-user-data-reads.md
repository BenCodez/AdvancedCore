# Shared user-data read policy

This is the first user/storage extraction step, not a complete storage or reward
port. AdvancedCore remains one project with the existing POM and public APIs.
No workflows, dependencies, schema migrations or native-loader stubs are added.

## One implementation, existing state

`core.user.UserDataReader` contains the integer/string scalar read policy formerly
inside UserData. It uses the neutral `UserDataReadContext` and existing published
UserStorage/UserDataFetchMode enums and SimpleAPI Column/DataValue types.
`bukkit.user.BukkitUserDataReadContext` supplies the actual existing user/cache
objects, row providers and file access. UserData delegates its two scalar policy
methods to this implementation immediately; there is no shadow implementation
that only native consumers use.

The context is lazy and owns no second cache. The temporary map is the same
private UserData field used before, including its current replacement and mutation
behavior. This deliberately does not call an overridable getTempCache method in
place of the field. Public constructors and overloads are unchanged. Constructing
UserData with a null user remains possible for operations that do not dereference
that user. The actual cache object captured before cacheIfNeeded remains the one
read afterward, even when that callback replaces the user's cache reference.

The Bukkit context calls the facade's virtual getMySqlRow/getSQLiteRow/getData
methods, preserving subclass customization. UUIDs are still obtained from the
existing user when the storage operation occurs; no new identity resolver or
normalization policy is introduced.

## Deliberately preserved differences

- All six fetch modes retain their existing temp/user-cache/storage precedence.
  This extraction does not invent new waitForCache handling.
- Integer reads call cacheIfNeeded when the user cache exists. String reads do
  not; missing caches trigger cache() but are not immediately fetched a second
  time before the storage decision.
- Temporary integer values are not automatically converted into string values.
- Literal "null" strings remain unchanged in caches, while the existing SQL
  string path interprets them as empty strings.
- A matching invalid numeric SQL string returns the fallback rather than reading
  a later duplicate column. Existing SQL provider failures still propagate;
  existing flat-read exceptions still use the fallback.
- A known user-cache key with a null string value does not fall through to SQL.
- Null/empty key handling and type-specific fallback behavior remain unchanged.

These behaviors are retained to avoid mixing a semantic change into extraction.
Any intentional correction should be a separate, independently tested change.

## Unchanged and subsequent work

All setters, cache-write callbacks, asynchronous write ordering, bulk operations,
list encodings, queued/generated reward formats, database/table names and file
formats remain unchanged. This PR does not yet make the storage providers,
AdvancedCoreUser identity lifecycle, or the whole reward system portable.

The next steps can extract user identity/write contexts, storage providers and
reward definitions/execution while preserving these read fixtures. Native SQLite
support still needs the planned SimpleAPI SQLite boundary. Final native packaging
must include only dependency-clean classes; the full current AdvancedCore JAR is
not a Fabric/Forge artifact.

This change is independent of the executor-runtime extraction and can be reviewed
against master without stacking unrelated lifecycle changes.

## Verification

Run the existing `mvn -B -f AdvancedCore/pom.xml package` command. Existing user-data
tests stay intact. New tests cover every fetch mode, precedence, nulls, errors,
cache-refresh identity and subclass overrides. The same pure-Java fixture runs in
an isolated classloader without Bukkit, using the actual SimpleAPI data-value
classes. No new GitHub workflow is required.

These tests do not connect to live databases or Minecraft servers. A downstream
VotingPlugin build and live-server smoke test remain release compatibility gates.
