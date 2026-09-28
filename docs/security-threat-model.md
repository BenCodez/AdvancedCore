# AdvancedCore security threat model

This document defines the repository-specific threat model for security review and Codex Security scans. Read it with current source, tests, and AGENTS.md. AdvancedCore is a shared foundation, so a helper that mishandles lower-trust input can become a vulnerability in several downstream plugins even when AdvancedCore itself has no direct Internet listener.

Current code is the source of truth. Older security findings are useful attack ideas, but do not assume an old implementation still exists.

## Security objectives

AdvancedCore runs inside the Minecraft server JVM with the privileges of the consuming plugin and provides shared user persistence, SQL, identity, rewards, configuration, commands, GUIs, timers, permissions, backups, cross-server helpers, placeholders, and optional JavaScript.

The highest-value properties are:

1. Lower-trust values passed through a public API must remain data and not become SQL syntax, file paths, reward selectors, console commands, scripts, placeholders, or other executable syntax unexpectedly.
2. User-data reads/writes, caches, transactions, and callbacks must not allow duplicate rewards, lost debits, stale authorization, or cross-user state leakage under concurrency.
3. Permission and identity decisions must be correct at the final side-effect boundary, including offline-mode, Bedrock, proxy, and delayed/offline reward paths.
4. Reward execution must not be duplicated or redirected across retries, delayed work, world changes, reconnects, reload, or failure recovery.
5. Bukkit/Paper/Folia thread ownership must be preserved so ordinary interaction spam cannot become races, duplicated state, or server instability.
6. Generic filesystem, YAML, backup, SQL, and script helpers must not silently widen a lower-trust caller's authority.
7. Optional JavaScript is privileged operator code, but attacker-derived replacement data must not become executable script or leak another player's evaluation context.
8. Queues, caches, scheduled work, scans, recursion, and diagnostics influenced by players or persisted tainted data must remain bounded.

## Trust boundaries

### Lower-trust data

Treat as attacker-controlled or tainted unless the caller proves otherwise:

- player names and UUID-like strings;
- command arguments, GUI clicks, dialog/text values, sign/chat/placeholder results;
- values read from user databases or caches when they may have originated from votes, proxies, remote services, compromised backends, or previous unsafe writes;
- reward names/keys chosen indirectly by players or remote data;
- plugin/proxy/global-message payloads;
- values downstream plugins receive from Votifier, Redis, MQTT, sockets, plugin messaging, or shared databases;
- offline-mode and Bedrock identity strings;
- public API parameters when a benign downstream plugin may pass player- or vote-controlled values.

Authentication or persistence does not make a string safe for a different interpreter.

### Trusted operator input

Plugin YAML, reward files, configured console commands, database credentials/table prefixes, backup/report destinations chosen directly by an administrator, admin-only raw SQL, and explicitly enabled JavaScript are intentionally privileged.

Do not report an administrator configuring a dangerous command, script, or query as a vulnerability by itself. Report it when a lower-trust actor can influence that privileged input or reach the sink without the intended authorization.

### Other installed plugins

A fully malicious installed plugin already shares the JVM and is not meaningfully sandboxed. Public API footguns still matter because ordinary downstream plugins may pass lower-trust data into AdvancedCore assuming documented validation or escaping.

## Current controls to preserve

Current master already contains important fixes and architecture that older scan results may predate:

- the SQLite user path has prepared-statement regression coverage for values;
- dynamic SQL identifiers have explicit quoting or validation helpers in important user-storage paths;
- reward loading normalizes lookup names and validates safe reward filenames before constructing reward files;
- shared user/cache runtime uses explicit admission/gating and concurrent structures rather than the older simplistic cache model;
- JavaScript evaluation synchronizes access to the shared engine;
- inventory click execution has explicit scheduler behavior and tests;
- compatibility-sensitive Folia/player scheduling has regression coverage.

Security review should look for bypasses, inconsistent sibling paths, and new cross-feature races rather than simply restating the old vulnerability that motivated a control.

## SQL and persistent user state

High-risk areas include user storage, global SQL state, migrations, dynamic columns, queued writes, and public raw-query helpers.

### Values vs identifiers

Lower-trust values should be bound with prepared statements.

Table names, column names, type definitions, ORDER BY fragments, defaults, and DDL cannot be protected by value binding. They require strict construction, quoting, or allowlists appropriate to the database.

Search for:

- one SQLite, MySQL, or PostgreSQL sibling path that concatenates a value while others bind it;
- identifier quoting bypassed by type/default expressions or raw query fragments;
- a lower-trust user-data key becoming a column/schema identifier without validation;
- raw SQL helpers reachable through player or admin-lite input;
- asynchronous ALTER/migration steps racing reads and writes;
- partially applied migrations leaving authorization-relevant fields at defaults;
- error handling that converts a failed balance/permission read into an allow decision;
- stale caches overwriting newer shared-database values;
- transaction boundaries that allow reward effects without durable debits or limits.

Admin-only raw SQL is intentional. Its existence is not a remote SQL-injection finding.

### User cache and concurrency

Trace one mutation through admission, cache read, validation, storage write, cache update, callbacks, and flush/recovery.

Look for:

- lost updates between async writers;
- stale reads used for purchase, reward, or permission decisions;
- lock-order inversions or callbacks while holding shared locks;
- shutdown dropping accepted queued mutations;
- duplicate change/listener callbacks for one logical mutation;
- callbacks delivered on an unexpected thread where downstream plugins may grant rewards or call Bukkit APIs;
- cache eviction or reload allowing stale data to become authoritative again;
- one UUID/name mapping mutating another user's cache.

## Filesystem, YAML and backup boundaries

Generic file helpers are powerful because downstream code may supply names and paths.

Reward loading currently validates safe reward filenames. Test all alternate reward-loading, creation, and editing paths for the same invariant, including directly defined rewards, folders, migrations, GUI editors, and public APIs.

Search for:

- parent traversal, absolute paths, separator variants, Unicode normalization, Windows drive/UNC syntax, or symlink tricks bypassing containment;
- check-then-use races between validation and file open/move;
- a validated child path later converted back to an untrusted string and re-resolved;
- generic file writers receiving player/vote values through a benign caller;
- backup/report creation following symlinks into secrets or unrelated plugin data;
- archives/logs including credentials, database URLs, tokens, or excessive player data;
- YAML keys or section paths derived from lower-trust input altering a different subtree than intended.

Creating a file at an administrator-selected path is trusted behavior. Path traversal becomes security-relevant when the name/path crosses from a lower-trust boundary.

## Rewards and command execution

Configured reward commands are intentionally privileged.

The dangerous boundary is selection and substitution: can lower-trust data choose a reward/command, change its arguments, or become executable syntax?

Review string reward shortcuts, nested/random/choice rewards, directly defined rewards, offline/retry rewards, world-branch rewards, delayed rewards, reverse/negated requirements, per-player and bulk rewards, and placeholders inserted into console/player commands.

Search for:

- player/vote data selecting an arbitrary reward name;
- a lower-trust string being reinterpreted as a console-command shortcut;
- multi-pass placeholder expansion where substituted data becomes a second placeholder/token;
- requirement failure incorrectly queuing a reward for later claim;
- branch rewards being saved for worlds or conditions not selected at trigger time;
- duplicate execution after reconnect, retry, callback duplication, or scheduler rejection;
- reward effects occurring before a limit/debit is durable.

Do not flag operator-authored console commands merely because they are powerful.

## Commands, GUIs and permissions

Command framework registration is not enough; authorization must still be correct where side effects happen.

Review admin/editor commands, GiveAll/bulk operations, SetData, RunSQLQuery, JavaScript, reward editing, user-targeting commands, and GUI callbacks.

High-value cases include:

- self-vs-other-player permission confusion;
- special values such as all widening scope without explicit bulk permission;
- console/player sender assumptions;
- a GUI rendered with permission but clicked after permission/config changes;
- stale GUI state applying to the wrong player, path, or reward after reload;
- overlapping click callbacks duplicating effects;
- hidden/admin-only buttons whose callback lacks an independent permission check;
- command aliases reaching a less-protected handler.

Current inventory behavior has explicit scheduler semantics. Do not report async callbacks generically. Show the concrete Bukkit/Folia API or stateful operation that becomes unsafe, the reachable click path, and the integrity or availability effect.

## Identity, permissions and offline players

Minecraft online usernames are normally constrained, but AdvancedCore is also used in offline, proxy, and Bedrock environments.

Review exact-name/UUID lookup, Bedrock prefixes, case normalization, cache mappings, offline permission checks, pre-authentication integrations, and permission requirements on delayed/offline rewards.

Search for:

- Java and Bedrock identities sharing a normalized base name;
- case collisions in offline UUID generation;
- fallback lookup selecting the wrong stored account;
- untrusted prefix-only Bedrock names treated as authenticated identities;
- reverse/negated permissions behaving incorrectly for offline users;
- pre-login events or rewards firing before an authentication plugin confirms ownership;
- stale permission snapshots granting rewards after permissions change.

Require a concrete account, reward, or permission effect.

## Placeholder and JavaScript boundary

Placeholder parsing must preserve literal data and avoid unintended second evaluation.

Search for:

- player/provider output inserted and then reparsed as PlaceholderAPI or script;
- escaping/parity bugs turning inert percent/backslash text into executable tokens;
- attacker-selected placeholder identifiers causing unbounded cache/state or expensive work;
- script bindings leaking between users/evaluations;
- persistent globals in a shared script engine changing later evaluation behavior;
- concurrency around a shared engine or binding cleanup;
- JavaScript error/log paths exposing sensitive objects or data.

JavaScript is administrator-authored privileged code and is not a sandbox. If enabled and supplied with Bukkit, AdvancedCore, or console objects, broad power is intentional. The security issue is an authorization/input crossing into script execution, not the script's power itself.

## Cross-server and messaging

AdvancedCore can sit underneath systems that receive plugin messages, Redis/MQTT/socket traffic, or shared database state.

Do not assume a message is trusted merely because it arrived through an internal helper. When AdvancedCore itself authenticates or encrypts a transport, test the actual guarantee. When authentication belongs to the consuming plugin, avoid inventing a missing AdvancedCore security contract.

Look for source/origin confusion, replay/duplicate callbacks, one server overwriting another's state without version/ownership checks, shared global state allowing a compromised backend to trigger privileged changes, encryption treated as authorization, and malformed payloads crossing into generic config/command helpers.

## Resource exhaustion and lifecycle

Tainted persisted data can be as dangerous as live player input.

Prioritize:

- one scheduled task per database/reward row without a useful bound;
- all-user scans/sorts on the server or region thread;
- large YAML/list/map recursion;
- unbounded GUI button creation;
- cache cardinality controlled by player-selected keys;
- retry loops after persistent database failure;
- executor queues with no admission bound;
- cancellation/reload leaving stale callbacks alive;
- repeated malformed data causing log amplification.

Reload/disable must retire old executors, listeners, timers, caches, and callbacks so stale work cannot mutate replacement state.

## Logging, secrets and supply chain

Do not log database passwords, credential-bearing URLs, tokens, private keys, full sensitive configuration, or raw lower-trust control characters.

CI findings are security-relevant when untrusted PR code receives write-capable credentials or can influence trusted release artifacts. Mutable action tags and dependency policy should be calibrated to actual privileges and the release path rather than reported as runtime RCE.

## High-value attack stories

1. A downstream plugin stores player-controlled data keys and values across SQLite, MySQL/MariaDB, and PostgreSQL; compare all syntax/value boundaries.
2. Two async operations mutate the same user balance while reload or cache eviction occurs.
3. A lower-trust reward name reaches every reward load/create/edit path using traversal, separator, and Unicode variants.
4. A reward with multiple conditional branches fails one requirement; verify failed branches cannot be claimed later unless explicitly designed.
5. Two GUI clicks overlap around a debit, reward, or config write and test duplicate effects.
6. An offline or Bedrock identity collides with another stored user and triggers a permission/reward decision.
7. Player/provider placeholder output contains another placeholder or script token and crosses every formatting layer.
8. Two users execute JavaScript-backed evaluation concurrently and sequentially; verify bindings/globals do not cross unexpectedly.
9. A large tainted DB/YAML dataset triggers timers, GUI rendering, or scans; verify work is bounded and off-thread.
10. Reload/disable occurs with queued user writes, delayed rewards, inventory callbacks, and scheduler tasks in flight.

## Scan calibration and severity

Critical: ordinary-player or unauthenticated input reaches arbitrary console command execution, JavaScript/JVM execution, arbitrary host/plugin file write, or SQL syntax capable of modifying unrelated data.

High: permission bypass to privileged/bulk operations; lower-trust path traversal with meaningful file access; cross-server spoofing causing privileged effects; repeatable reward/economy duplication; identity confusion causing significant reward/permission theft; persistent resource exhaustion with practical server outage.

Medium: prerequisite-heavy reward/identity integrity failures; bounded scheduler/DB DoS; meaningful secret leakage to limited readers; placeholder/script injection with configuration-dependent effects; threading races causing occasional duplicate/lost effects.

Low: trusted-admin footguns, malformed trusted config, generic public API misuse requiring a malicious installed plugin, minor log injection, or build hardening without a privileged token/artifact path.

Usually not security by itself: API/ABI compatibility regressions, operator-authored SQL/commands/scripts, ordinary malformed YAML exceptions, cosmetic GUI issues, or a thread-policy concern without a concrete reachable side effect.

For every finding identify the lower-trust source, public/helper path, final privileged sink, current mitigation that failed, and whether current master still exposes the issue.
