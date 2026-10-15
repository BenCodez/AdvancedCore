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
