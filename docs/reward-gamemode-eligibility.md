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
