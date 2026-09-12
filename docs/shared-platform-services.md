# Shared platform services

The `core.platform` package now has minimal `PlatformServices`, `PlatformPlayer`,
and `PlatformScheduler` contracts. `BukkitPlatformServices` adapts the existing
plugin-owned SimpleAPI Bukkit/Folia scheduler; it does not create another runtime,
executor, service registry, persistent user, or player cache.

## Native consumers

Shared code receives `PlatformServices` through its constructor. The Bukkit entry
point supplies `new BukkitPlatformServices(() -> plugin)`; a future loader supplies
its own implementation. Construction does not resolve the plugin or touch Bukkit.
There are no Fabric, Forge, or NeoForge implementations or new artifacts yet.

```java
platform.scheduler().runPlayer(playerId, player -> {
    if (player.hasPermission("example.message")) {
        player.sendMessage("Already-rendered message");
    }
});
platform.scheduler().runServer(() -> platform.dispatchConsoleCommand("say hello"));
```

`findOnlinePlayer(UUID)` is a read-only online-session lookup, not offline UUID/name
resolution or an `AdvancedCoreUser` lookup. It does not invoke Vault, offline
LuckPerms checks, SQL, or FLAT storage. The player permission method checks one
literal native node and does not add prefixes, split alternatives, or grant OP.
Existing AdvancedCore permission helpers and their provider/fallback rules remain
unchanged.

Player operations must run on that player's owning game thread. A synchronous
lookup does not grant thread ownership; asynchronous consumers should use
`runPlayer`. Submission only locates the entity by UUID; it does not read player
state such as `isOnline()` on the caller's thread. Online/session checks run inside
the captured entity's scheduler callback, not on the global or location scheduler.
If that login disconnects, is replaced, or the plugin is disabled before the
callback, it will not execute the action. A stale handle
returns false for permission checks and rejects messages/commands; it does not
look up a replacement login and redirect the operation.

Server-wide callbacks must not access region-confined worlds or players. Console
command dispatch belongs in that context, but called commands are still responsible
for their own Folia safety. Raw message/command methods do not expand placeholders,
color codes, JSON, or JavaScript. `sendMessage` is the basic native text transport,
not a replacement for AdvancedCore's rich-message renderer.

## Scheduling contract

`runServerLater` uses **seconds**, matching SimpleAPI's existing `runTaskLater`
overload. It is not a tick count and does not guarantee a wall-clock deadline.
Negative delays are rejected. `runServer` uses the existing `runTask` handoff.
`runPlayer` never supplies a null entity to SimpleAPI (which would fall back to
global execution). The plugin's existing scheduler owns shutdown cancellation;
no parallel lifecycle or background work is introduced here.

A false `runPlayer` result means UUID lookup found no player to schedule. A true
result means submitted, **not completed** or confirmed online. Disconnect/retirement
or shutdown can prevent callbacks from running. Rejection and callback exceptions
are not swallowed. These methods provide no durable completion acknowledgement,
retry policy, persistence, timeout, or exactly-once guarantee; do not use their
return values to checkpoint rewards. Ordered asynchronous reward execution and
its completion boundaries remain separate work in PR #317.

## Existing production integration

Only the private legacy `MiscUtils.runConsoleCommand` implementation delegates to
`ConsoleCommandDispatcher`, using a lazily resolved Bukkit adapter. Existing public
command-list helpers exercise the new shared scheduler and command service now.

The caller's placeholder/script parsing, logging, list iteration, and public
signatures are unchanged. The shared dispatcher removes exactly one leading slash
and preserves the old stagger branch: a positive delay uses the delayed scheduler
only when staggering is enabled. All other cases use `runTask`. It does not filter
null/empty results, re-render text, or turn scheduled submission into completion.
Single-command overloads, the separate slash helper, and async reward methods are
not changed. PR #317 also touches `MiscUtils`; preserve its async additions when
combining branches, and rerun both suites.

## Validation

The existing command remains `mvn -B -f AdvancedCore/pom.xml package` on Java 21.
Tests cover core command policy in a JDK-only fixture and isolated classloader,
Bukkit UUID lookup/native dispatch, entity-versus-global routing, stale sessions,
disable, failures, lazy ownership, worker-thread submission with no early player
state reads, already-disconnected submissions, and the existing command-list facade.
No new workflow, module, dependency pin, or test skip is introduced. SimpleAPI remains
`1.0.2-SNAPSHOT`; record the actual resolved snapshot when validating downstream.

Before release, build SimpleAPI, AdvancedCore, and VotingPlugin against the actual
candidate artifacts and test the packaged plugin on Bukkit/Paper and Folia.
Mocked scheduler routing and core classloader tests are not live-server or
packaged-native-mod validation.
