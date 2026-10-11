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


## Reproducing the dependency build

SimpleAPI's SQL lifecycle fix is merged as
`fdbf26eec885ec3f1c1dbd937a243a0acfaa0a5e` (PR #111). The public
`1.0.2-SNAPSHOT` can still resolve to an older build without the required API.
GitHub CI checks out this exact reviewed commit, runs its full `clean install`,
then builds AdvancedCore against the installed artifact with snapshot refresh
disabled (`-nsu`), so the older remote snapshot cannot replace it. A failed dependency
build stops the consumer build. It does not deploy either artifact or change
runtime configuration.

For a local build, first check out that SimpleAPI revision and run
`mvn -B -f SimpleAPI/pom.xml clean install` in its checkout, then run
`mvn -B -nsu -f AdvancedCore/pom.xml clean package` in AdvancedCore. Normal server
installation uses the shaded AdvancedCore JAR; no separate runtime SimpleAPI
installation is added. This CI source pin can be removed once the repository's
configured snapshot reliably includes the corrected lifecycle API, or updated
explicitly alongside a later dependency change.
