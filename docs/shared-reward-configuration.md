# Shared reward configuration reads

`core.rewards.RewardConfigReader` interprets reward settings through SimpleAPI's
`StructuredConfigView`. It does not open files, create users, schedule work, or
execute rewards. The supplier is evaluated on each read so a replaced root can
be observed without rebuilding the reader; the caller owns synchronization.

## Existing Bukkit callers

`RewardFileData` retains its constructors, public method signatures, native
section getters, file handling, setters, and generated-snapshot behavior. Its
portable setting getters delegate to `BukkitRewardConfigReader`, which reads
through the existing virtual `getConfigData()` method. Nothing is resolved during
construction. Subclasses, `setConfigData`, and file reloads keep their current
behavior, including the existing distinction between constructor and reload
case handling.

The Bukkit adapter preserves implicit default-tree getters and raw list identity.
The existing `ArrayList<String>` casts remain, including their historical failure
for other list implementations. This extraction does not silently filter native
objects, coerce command entries, or change list mutability. Native display/item
sections stay in the existing Bukkit API. Reward-derived permission defaults also
remain in that facade; native readers can supply a reward name explicitly.

## Native readers

```java
StructuredConfigView view = new ConfigurateStructuredConfigView(node);
RewardConfigReader settings = new RewardConfigReader(() -> view);
List<?> consoleCommands = settings.getCommandsConsole();
boolean delayed = settings.getDelayedEnabled();
StructuredConfigView nested = settings.definitionAt("Choices", "daily.bonus", "Rewards");
```

Use imports from `com.bencodez.advancedcore.core.rewards` and
`com.bencodez.simpleapi.core.config`. The supplied view controls casing, paths,
and typed conversion rules; use the appropriate SimpleAPI case-insensitive view
when required. `definitionAt` uses literal keys, so names containing dots are not
split. The named choice/item helpers retain the existing dotted-path convention.

Plain-data lists from the neutral reader are detached and unmodifiable, retain
entry order and value types, and use SimpleAPI's bounded exports. Unsupported
native objects are rejected by that export contract. This is intentionally not
an emulation of Bukkit's native object/list identity. No command is executed by
reading it. Empty/malformed non-list settings return an empty list.

## Dependencies and validation

This source requires SimpleAPI's merged structured configuration APIs (SimpleAPI
PR #78 and its follow-ups). The AdvancedCore dependency pin must resolve an
artifact containing `StructuredConfigView`, `BukkitStructuredConfigView`, and
`ConfigurateStructuredConfigView` before a normal full build can succeed. Do not
substitute an invented timestamp, weaken the dependency-pinning regression test,
or assume that a mutable local snapshot proves the pinned build works.

Tests cover portable setting/nested-definition behavior, an isolated runtime
that rejects Bukkit and JUnit, native Bukkit defaults and list semantics,
replacement/reload/subclass compatibility, and real Bukkit/Configurate parity.
Use the repository's normal Maven package command and verify these tests actually
run; a successful invocation with zero discovered JUnit tests is not validation.
A downstream VotingPlugin build must resolve the exact candidate AdvancedCore
and SimpleAPI artifacts, not unrelated cached snapshots.

This is one Maven project with `core` and `bukkit` packages. It does not add a
shared AdvancedCore artifact, a loader implementation, or a new workflow.
Storage/FLAT removal, user extraction, reward execution (including PR #317), and
native mod packaging remain separate work. PR #314 is not reopened.
