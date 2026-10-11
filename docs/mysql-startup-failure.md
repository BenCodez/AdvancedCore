# MySQL startup failures

User storage remains fail-closed: a rejected MySQL connection aborts startup. It
never selects SQLite as a fallback. Correct the configured database credentials
or server permissions before restarting; existing tables and user data remain
unchanged.

This fix requires the accompanying SimpleAPI SQL initialization fix. Its table
constructor retains the original connection failure and avoids invoking subclass
logging hooks before AdvancedCore assigns its plugin reference. AdvancedCore
continues to initialize its table, caches and UUID schema after that assignment.
If subclass initialization subsequently throws, it closes the owned SQL pool and
workers and preserves the primary failure, attaching cleanup failures as suppressed
exceptions. The platform-neutral MySQL backend already uses the same deferred
initialization boundary and owns equivalent failure cleanup.

No configuration or schema migration is required. Pool initialization remains the
existing startup operation; this change does not move startup to a background
thread or change successful storage or reward processing.

The proxy/global-data SQL constructor had the same rejected-connection continuation
pattern. It now aborts before invoking its subclass diagnostics or issuing queries,
and closes the pool if initial database selection fails.


## Building against the published dependency

The POM pins published SimpleAPI `1.0.2-20261011.161601-40`, which includes the
merged SQL lifecycle fix from PR #111 (`fdbf26eec885ec3f1c1dbd937a243a0acfaa0a5e`).
A normal clean checkout can run `mvn -B -f AdvancedCore/pom.xml clean package`
without checking out or installing SimpleAPI manually. CI uses the same dependency
resolution path. The immutable timestamp avoids older rolling snapshot cache entries.

Normal server installation uses the shaded AdvancedCore JAR; no separate runtime
SimpleAPI installation, configuration change, schema migration or storage fallback
is added. The dependency must be available from the configured Maven repository.
