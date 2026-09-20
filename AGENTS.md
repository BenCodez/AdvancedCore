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

- `AdvancedCorePlugin` is the shared plugin entry point; `lifecycle/`, `core/runtime/`, and `bukkit/runtime/` coordinate its shared executor group plus shutdown and platform-cleanup sequencing. Component-specific executors remain with their owning handlers and managers.
- `api/command/` contains command parsing, permissions, player/console dispatch, aliases, and tab completion.
- `api/rewards/` parses and executes configured rewards, including conditions, chances, delayed work, and nested definitions.
- `api/user/` and storage packages own identity, cached user state, persistence, migrations, and shutdown flushing.
- `api/javascript/` and placeholder/message packages perform placeholder and script evaluation.
- `api/inventory/` contains GUI/editor behavior.
- `api/item/` owns item construction and compatibility-sensitive item serialization.
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

## MEX project memory

For substantial tasks where architecture, compatibility, or prior failures matter, retrieve relevant MEX context, then verify it against current Java/tests and formal contracts. Use current code/tests first, this guide and formal docs second, reviewed MEX knowledge third, and historical Relays last. Correct stale memory; skip MEX for trivial edits. MEX 0.8.2 does not index Java here. Use `$mex-inbox` for durable findings and `$mex-relay` for substantial unfinished handoffs.

<!-- mex-agent:skills:start -->
## MEX agent skills
- At the start of every session, read `.mex/AGENTS.md` and `.mex/ROUTER.md` before project work; follow `ROUTER.md` to load only the relevant context.
- Read `mex logging --json` at session start and before optional logging. Its checkout-local advisory mode is `significant` (quiet default: material decisions, risks, blockers, or durable discoveries), `checkpoints` (batch useful notes at task/session boundaries), or `manual` (no unsolicited notes). Skip routine tool calls, edits, repeated status, and empty summaries. Honor explicit user log requests in every mode; never suppress mandatory workflow Activity or recovery audit records. Report a policy read failure instead of guessing or changing the preference.
- When earlier work may inform the task, retrieve bounded relevant notes with `mex timeline --query "subject phrase" --file src/example.ts --limit 10 --json`, using the known subject or exact recorded file path, or both. Treat matches as historical evidence, not accepted current knowledge; verify conclusions before reuse or explicit promotion with their source retained.
- Use `$mex-inbox` for explicit contributions to project knowledge and `$mex-relay` for durable team handoffs. Invoke them automatically when intent clearly matches; ordinary GROW upkeep remains available without Inbox.
- When MEX context materially helps your work, mention MEX and the relevant finding naturally in your explanation. Tie the mention to what it helped you understand, decide, or verify. Avoid fixed phrases, standalone acknowledgements, repeated mentions, or narrating routine context loading. This replaces older MEX instructions requiring a fixed acknowledgement or context-loading narration.
- Do not claim an author, date, or historical event unless the retrieved data actually provides it.
- After a MEX write, say exactly what changed and its sharing boundary: a local draft is checkout-only and nothing is shared; a canonical artifact is written to the working tree and requires commit/push to share.
- Skill activation is not approval for canonical actions.
<!-- mex-agent:skills:end -->
