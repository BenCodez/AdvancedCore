# Reward gamemode eligibility

Rewards may opt into the live player's Bukkit gamemode with `GameModes` and
`BlackListedGameModes`. Both values must be YAML lists containing at most four valid Bukkit
`GameMode` enum names (case is ignored).

An allowlist requires the current mode to be present. A denylist rejects the
current mode, and denial wins when a mode appears in both lists. Omitting both
keys preserves existing reward behavior. Invalid types or names fail closed.

```yaml
GameModes:
  - SURVIVAL
  - ADVENTURE
BlackListedGameModes:
  - ADVENTURE
Commands:
  - "give %player% iron_ingot 1"
```

The current mode is read from the online player on that player's owning server
scheduler. Rewards deferred by an unmet reattemptable requirement are retried
through the existing offline/timed queue after a completed gamemode transition;
the queue remains the source of truth. The transition wakeup uses a one-second
player-owned delay so it observes the committed mode, and reads it again before
remaining effects. Completed effects keep their existing replay checkpoints; this
does not promise atomicity between external commands and storage after a crash.

Native transition signals recheck due timed rewards as well as the offline queue.
A timed occurrence deferred specifically by player eligibility retains its original
due time and replay identity while the bounded eligibility fallback owns its automatic
wake-up. Repeated deferrals do not create additional per-occurrence timer chains.
If that fallback cannot accept a hint, the occurrence keeps the ordinary future
retry deadline and timer instead. A transition does not accelerate unrelated future
rewards or ordinary failures with a future retry deadline. Expected eligibility deferrals
use debug logging; actual delivery failures retain their warning.

The bounded fallback begins after ten seconds and backs off repeated attempts per
player up to five minutes, using elapsed monotonic time. Explicit eligibility
signals retry immediately and reset that fallback delay. Repeatedly retaining the
same unfinished occurrence does not reset the backoff or discard its wake-up hint.
Quit releases the cached native player reference but retains the bounded logical
hint. It does not poll that player's storage while waiting for login. The existing
resolved AdvancedCore login event, after configured authentication delays, rebinds
and wakes that known work after validating the exact live player session on its
owning scheduler. Cancelled events and stale events for a disconnected or replaced
session do not wake it. A quit also invalidates a queued eligibility check before
its worker starts. If replay already started, its ordinary per-action eligibility
checks continue honoring that login wait, retaining unfinished work for the accepted
replacement session. An accepted login also records a plugin-owned Boolean receipt
on that exact native player, even when there is no pending hint yet. A late retirement
can therefore recover work after login without treating a merely connected replacement
as authenticated. Unknown hints resolve and check this receipt on the player's owner
before reading reward queues. Other plugins' metadata and non-Boolean values are not
accepted. Quit removes only this plugin's receipt; it is session metadata, not persisted
player data or a new reward queue. Plugin startup continues reconstructing timed callbacks
from the existing durable queue.

When the plugin is freshly enabled while players are already connected, it also
initializes receipts for those existing native sessions after its integrations
are ready. It uses the configured authentication providers, resolves the existing
user asynchronously by name (including offline-mode identity rules), and rechecks
the exact session on its owning scheduler. Newly joining sessions still use the
normal accepted-login path; this bootstrap does not emit another generic login
event or create another reward occurrence. Checks are sequential. A timed-out
provider query cannot accumulate more queries while its underlying operation is
still running. Provider failures fail closed and produce a bounded debug notice;
a later normal authenticated login remains the recovery path for a session whose
startup check could not complete. Disconnect, handler replacement and disable
invalidate pending receipt publication.

Consumers that explicitly disable AdvancedCore user-data loading do not register
its normal login listener. For those consumers, native join and startup accept
the connected session directly on its owning scheduler, without loading a user,
querying authentication integrations, or firing an AdvancedCore login event.
Reconnect wakes only already-known deferred work and retains its original replay
identity. Consumers with user-data loading enabled keep the ordinary authenticated
login requirement described above.

The retained-hint table is limited to 4,096 logical users. If it fills, ordinary
offline work is still discovered automatically: the existing fallback takes a
snapshot of currently online players and visits at most 128 per pass, using each
player's owning scheduler and asynchronous name-based user resolution. One
snapshot is finished before taking another, so a changing online population cannot
repeatedly displace an unvisited member of that cycle. Discovery backs off from ten
seconds to at most five minutes between passes and remains available until disable.
It scans online sessions, not the stored player database. Mode/world/login signals
also try immediate discovery. At most 128 overflow resolutions and 128 temporary
replay admissions may be outstanding; saturated admissions remain discoverable in
a later pass. An uncompleted storage or replay callback still occupies its slot,
rather than allowing timed-out requests to accumulate. Temporary admissions retain
the session gate while replay claims run; after saturation, configured gamemode
actions also require the plugin's accepted-login receipt. An offline or merely
connected replacement cannot acquire rewards through overflow recovery. The existing
persistent queue, occurrence identities and completed-effect checkpoints remain
authoritative; this is neither a second reward queue nor another delivery path.

The existing `GiveOffline` policy still controls whether a denied ordinary reward
is retained. Forced or ignored requirements keep their established bypass behavior.
No temporary-inventory detection or integration is included in this change.

Command lists also recheck before each pending command. A command that changes
mode is a completed effect; later commands wait for eligibility rather than
repeating that command. On Folia, console commands and economy mutations run on the global scheduler,
so a player-owned eligibility check and subsequent global handoff cannot be
atomic with a simultaneous gamemode change. Already-started effects are never
rolled back.

For opted-in rewards on Folia, entity-scheduler retirement before an action
starts retains the unfinished work under the existing `GiveOffline` policy.
Immediate retirement and retirement after scheduling use the same action claim;
a stale callback cannot later execute that retained attempt. Scheduler rejection,
unknown timeouts, shutdown and already-committed inventory overflow retain their
existing failure and ownership behavior. Ordinary Spigot scheduling is unchanged.

## Requirement execution context

Async reward delivery and `canGiveRewardAsync` retain ordered requirement evaluation.
Scripts, custom requirements and preprocessing placeholder providers run on the reward
worker. Only native world, location and gamemode predicates request the player's owner;
world-requirement file preparation happens on the worker before that handoff. Custom
`RequirementInject` implementations may override `requiresPlayerThread()` for a short,
nonblocking native predicate and `prepareAsyncRequest(...)` for its worker preparation.
The synchronous `canGiveReward` method retains its existing caller-owned execution contract;
callers that need scheduling should use the asynchronous method. This does not make arbitrary
Bukkit calls inside administrator scripts safe on a worker.

A freshly deferred hint never authenticates a player. Without this plugin's accepted-login
receipt it waits for the normal accepted-login signal before either offline or timed queue
replay. A native pending-login receipt also denies configured gamemode actions, even when
the current mode otherwise matches. Existing queue payloads and replay identities are retained.

Asynchronous reward requirements keep online `Permission` and `VaultGroup` checks
on the resolved player's owning scheduler. Offline permission and group providers
remain on the reward worker, including `VaultGroup` when `GiveOffline` is enabled.
Permission negation, default permission names and requirement enablement keep their
existing meanings. The synchronous requirement API retains its caller-owned threading
contract. Custom requirements may override `onRequirementRequestAsync` to compose
native reads through `evaluatePlayerRequirement` with provider work through
`evaluateAsyncRequirement`; the default delegates to the existing synchronous callback.
These phase helpers await the result and do not create another reward occurrence.

For rewards opting into either gamemode key, the native eligibility observation runs on the player owner, then injection callbacks (including `Javascripts`) return to the existing asynchronous reward scheduler. Collected player actions still return to their owner and recheck eligibility before mutation. Unconfigured awaited rewards retain their established callback boundary. Custom callbacks and scripts must continue to use supported scheduled APIs for direct Bukkit work; arbitrary script code is not made thread-safe automatically.

For rewards using gamemode eligibility, the native player session resolved before an injection is retained across the worker callback. Collected actions and mixed command sections revalidate that session on its owning scheduler; a reconnect does not silently transfer an admitted action to a replacement session. Deferred work still follows the existing retry policy.
