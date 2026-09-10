# Maintainer and AI-agent guide

AdvancedCore is a shared Bukkit/Paper/Folia library embedded by plugins such as VotingPlugin, MCPerks, and GraveStonesPlus. Changes can affect downstream plugins even when this repository builds successfully, so preserve public behavior and packaged compatibility unless a breaking change is explicitly authorized.

## Build and verification

Requirements: JDK 21+ and Maven. The Maven project is in the `AdvancedCore/` subdirectory.

```shell
mvn -B -f AdvancedCore/pom.xml test
mvn -B -f AdvancedCore/pom.xml package
```

Confirm the current workflow and POM before relying on these commands. Do not use deployment, install, or developer-copy profiles for routine validation. Verify that the package invocation produced a fresh JAR, record discovered test counts, and run `git diff --check`.

## Architecture and compatibility boundaries

- `AdvancedCorePlugin` owns the shared plugin lifecycle.
- `api/command/` contains command parsing, permissions, player/console dispatch, aliases, and tab completion.
- `api/rewards/` parses and executes configured rewards, including conditions, chances, delayed work, and nested definitions.
- `api/user/` and storage packages own identity, cached user state, persistence, migrations, and shutdown flushing.
- `api/javascript/` and placeholder/message packages perform placeholder and script evaluation.
- `api/inventory/` contains GUI/editor behavior and item serialization.
- SimpleAPI is a lower-level dependency. Verify its pinned contract instead of assuming an unreleased or unmerged API.

Treat public classes, constructors, methods, return values, callback threading, configuration shapes, serialized data, and shaded packages as compatibility surfaces. Before changing one, search downstream usage in VotingPlugin and other affected repositories when authorized.

## Runtime invariants

1. Never block the Bukkit/Paper/Folia server or region thread on database, network, or long-running filesystem work.
2. Do not access Bukkit entities, inventories, worlds, or other thread-confined state from arbitrary async callbacks.
3. Reward execution must not be lost or duplicated across delay, disconnect, reload, retry, or partial failure. Preserve the component's documented delivery contract.
4. Read-only lookups must not create users, mutate caches, or trigger persistence unless that behavior is part of the established API.
5. Keep user identity and UUID resolution consistent across online/offline paths. Coordinate cache updates, database writes, resets, and shutdown flushing.
6. Reload and disable must cancel or retire tasks, listeners, executors, connections, and callbacks without allowing stale work to mutate new state.
7. GUI/editor actions must recheck permissions and ownership and must not duplicate items, lose configuration, or save to the wrong path or user.
8. Optional integrations must be guarded against absent classes and incompatible versions. One platform must not load another platform's API accidentally.

## Placeholders and JavaScript

Placeholder parsing has compatibility-sensitive edge cases:

- preserve literal modulo/percent text and whitespace-containing PlaceholderAPI tokens;
- preserve escape parity rather than consuming or duplicating backslashes;
- isolate script bindings per evaluation so player/provider state cannot leak between requests;
- continue resolving non-script placeholders when JavaScript evaluation is disabled;
- do not introduce a second evaluation pass that turns substituted player/provider data into executable script;
- distinguish trusted administrator-authored script from untrusted replacement data.

Add focused regression tests whenever parsing order, escaping, tokenization, bindings, or disabled-engine behavior changes.

## Persistence, configuration, and resources

- Preserve existing YAML keys, defaults, casing behavior, migrations, and serialized forms unless a migration is included and tested.
- Make multi-step persistence transitions atomic or recoverable. Test failure before write, after write, before acknowledgement, during reload, and on restart when applicable.
- Bound queues, caches, input sizes, retries, and diagnostics. Handle executor rejection, cancellation, null/closed connections, and interruption explicitly.
- Do not hide failures with broad catches, ignored futures, or success results after partial failure.
- Never log credentials, database URLs, tokens, full configuration, or player data unnecessarily.

## Change and PR workflow

Keep changes focused and avoid formatting churn. Before any commit, push, PR update, review reply, or other remote change:

1. run relevant focused tests;
2. run the full Maven package build;
3. verify the fresh artifact and test discovery;
4. run `git diff --check`;
5. inspect the complete base-to-HEAD diff for compatibility, concurrency, persistence, lifecycle, security, and packaging regressions.

For substantive work, obtain a fresh source-read-only review. The implementation agent verifies and fixes accepted findings, reruns validation, and obtains a new review of the updated snapshot. Do not reuse an earlier clean verdict after changes, and do not merge without explicit authorization.
