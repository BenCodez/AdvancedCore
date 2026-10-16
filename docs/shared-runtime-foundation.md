# Shared runtime foundation

AdvancedCore remains one Maven project with one existing POM. New shared runtime
mechanics live under `core/runtime` and their platform contract under
`core/platform`; Bukkit cleanup hooks live under `bukkit/runtime`. No modules,
workflow files, dependency versions, publishing changes or game-loader stubs are
introduced.

The existing `AdvancedCoreLifecycle` facade now delegates its executor creation
and shutdown sequence to `core.runtime.AdvancedCoreRuntime` through
`BukkitRuntimePlatform`. AdvancedCorePlugin's current enable/disable calls already
use that facade, so Bukkit uses the extracted code immediately. There is no
parallel runtime with different caches or a second set of background tasks.

## Preserved contracts

- The facade constructor, nested RuntimeExecutors constructor and getters retain
  their signatures and return the same executor/scheduler objects.
- General, login and inventory work retain separate single-thread executors.
- BukkitScheduler/Folia behavior is unchanged; game/entity/region scheduling is
  not redirected to these background executors.
- JavaScript cleanup still happens before executor shutdown. Native providers
  close only after their storage worker and admitted preparation settle.
- Executor grace periods remain at most 2/2/2/1 seconds, followed by reward shutdown,
  forced executor shutdown and the existing unload/cache/inventory/integration
  cleanup order. The time-checker timer is captured at the same point.
- Individual component failures retain the warning/debug callback behavior and
  do not skip later components. Interrupted waits restore the interrupt flag.
- Lifecycle calls remain serialized by the owning platform. Consumer hooks and
  game/integration cleanup retain their lifecycle-thread execution.

## Shared shutdown deadline

All owned shutdown waits share one monotonic 15-second deadline captured at entry
into `AdvancedCorePlugin.onDisable()`. Pre-storage preparation, executor grace,
delayed rewards, forced-worker grace, and deferred watchdogs consume the same
remaining budget; a late preparation completion cannot start another grace
period. The public `RewardHandler.shutdown()` keeps its ten-second maximum for
standalone callers, while lifecycle shutdown supplies only its remaining budget.

Final inventory, server timestamp, and timed-permission mutations are admitted in
order on one bounded daemon persistence worker. Each publication stage settles
before its successor snapshot starts, including asynchronous adapters. Handler
admission is fenced immediately, and the worker remains available for ordered
successors until all final stages settle. `ServerData.mutateFinalData`
returns the actual file owner's publication stage. Native and completion-aware
final mutations are staged in a private YAML candidate. Native live YAML retains
a replacement that has become visible even if its directory sync then fails;
visibility alone does not acknowledge its reward receipt.
Native ServerData forces that candidate on the shutdown worker; an ordered
adapter serializes under its own YAML monitor and confirms its existing publisher.
Later unrelated saves cannot publish a failed final overflow mutation. Ordered
snapshots carry only successfully published final changes forward and preserve
newer explicit edits, including an edit that writes the same previous value.
No caller waits for publication while holding the YAML owner's monitor. Legacy
live read/mutate/save transactions use `ServerData.publicationMonitor()`, whose
native default is the file object itself. A facade must return its backing
owner's stable monitor and delegate YAML access, mutations and publication to
that same owner; overriding the monitor alone does not reroute inherited native
methods. Overflow-section replacement and recovered time-transition validation
use this contract so ordinary or final whole-file snapshots cannot interleave
with those compound live operations. This lock does not turn a queued or void
save into a durable acknowledgement and must never be held while awaiting an
asynchronous publication stage.

Legacy
void `saveData` overrides retain the established live-YAML mutation followed by
one save callback, but cannot claim confirmed publication without overriding the
additive completion contract; they report an unsupported confirmation. A legacy
callback failure remains observable, with no second mutation or save invocation.
Its live changes retain the old non-transactional behavior; candidate isolation
remains specific to native and completion-aware owners. For a known visible
native replacement, one immediate retry may settle only its retained directory
sync and cleanup, without repeating the mutation or move. Confirmation settles
the original publication successfully. If that retry fails, its stage fails and
the owner rejects subsequent saves, final mutations, reload and setup; later
cleanup cannot silently commit unacknowledged overflow. This fence does not
remove the separate file/receipt crash window or promise replay safety after an
uncertain restart. Failures before replacement retain their existing recovery
behavior. Inventory persistence gates user-storage
retirement and cannot acknowledge reserved replay overflow before its actual
save succeeds. The public synchronous handler shutdown methods remain available.
The shutdown-completion hook follows submission of final file saves; it is not a
durability acknowledgement. Its additive `onShutdownComplete(finalFilesStage)`
overload receives completion of all final file publications and invokes the
existing zero-argument hook on the same lifecycle thread by default. A consumer
retains its file owner until both pre-storage preparation and final-file stages
settle, including failure, then closes that owner. Worker termination must not be
part of either stage, which would create a completion/termination cycle.

Owned background executors and shutdown continuations use daemon threads so an
interrupt-ignoring JDBC call or filesystem operation cannot retain the JVM. A
timeout is reported while the actual stage stays pending. The native storage
owner remains open until its executing storage worker ends; accepted final file
saves are not discarded to meet the deadline. Preparation that settles after
or just before the deadline still admits its final checkpoint and retains the
daemon worker until that accepted retirement actually settles, without renewing
any caller wait. The runtime distinguishes an accepted native retirement from a
pending time transition or consumer hook; its watchdog cannot discard that
retirement's queued final task.
Durable completion can outlive plugin disable. While admitted shutdown work is
pending, a JVM hook registered with native file admission during startup gives
its existing owners the remainder of the same 15-second deadline during orderly
process exit, including disable inside an already-running host shutdown hook.
The hook performs no file writes or Bukkit calls and does not renew the deadline.
It waits at most 15 seconds from process exit as well, even if the host has not yet
started plugin disable. Successful ownership release or the disable deadline
removes it during a reload;
failed publication still retains the original failure and native admission fence.
An exit after the deadline, a hard process kill, or failure to register the hook
can still lose unfinished work. A timeout never means persistence succeeded.

The 15-second limit bounds AdvancedCore's waits. Synchronous consumer methods
(`onBeforeStorageShutdown`, `onUnLoad`, `onShutdownComplete`) and third-party
Bukkit/integration cleanup must return promptly: Java cannot safely preempt them
on the lifecycle thread. The temporary process hook waits only for already-owned
shutdown stages and never scans threads or calls other plugins. AdvancedCore
cannot guarantee a total server-stop time when another plugin or one of these
callbacks blocks.

Consumer `onBeforeStorageShutdown()` stages settle before shared user storage
retirement begins. A thrown exception, exceptional completion, or cancellation
still starts shared-cache retirement and waits for that retirement to settle.
The preparation failure remains the reported cause; a storage failure is retained
as a suppressed failure. Successful default hooks preserve the existing retirement
stage identity. A failing hook does not bypass accepted user-data flushing.

Delayed-reward admission closes before storage preparation. The additive
`RewardHandler.beginShutdown()` stage reports actual delayed-worker termination;
the existing bounded shutdown methods still cancel queued timers and interrupt
the worker when their wait ends. An interruption request does not release native
storage while an admitted check continues running. After that worker exits, a
manager-worker barrier includes checks it deferred under Spigot/Folia thread
classification and snapshots the existing per-user persisted replay tails.
Those tails include claim/checkpoint acknowledgement or restore callbacks that
can outlive timer execution. Final storage retirement is queued only after those
callbacks finish, so callback-held storage admission can unwind before the final
writer runs. Failed or cancelled preparation and time retirement retain this
ordering and still report their failure. This uses the existing replay owner;
it adds no reward engine or universal delivery guarantee.

## Native ServerData successor admission

Enable acquires native `ServerData.yml` admission before `onPreLoad()` and the
native load hook. Calling `loadHook()` directly acquires the same admission before
constructing the file owner. A successor using the same normalized absolute
platform data path fails immediately while its predecessor owns admission; it
does not wait for the predecessor or canonicalize/read the filesystem on that
admission path. Retrying enable after successful predecessor retirement is safe.
Repeated disable calls cannot submit new final writes after admission is released,
and a completed shutdown allows the same instance to acquire a fresh generation
where its existing consumer lifecycle supports re-enable.

Shaded copies share a bounded JVM Properties namespace with at most 256 opaque
String tokens. No plugin, classloader, future or callback is stored there. Each
owner releases from the exact Properties object into which it acquired its token.
The key uses a digest of the lexically normalized platform path, without file
contents. Installed plugins must preserve the JVM Properties ownership entries
and use the same platform data path; alias paths through symlinks and replacing
the JVM Properties map are not an interprocess or filesystem-lock protocol.
Separate JVMs are outside this admission guarantee.

Successful release requires confirmed final inventory/timestamp/permission
publication, consumer preparation, user-storage retirement, persistence-worker
termination and successful return from the unload/completion hooks. It also
requires actual termination of the native executor identities captured before unload can replace
their fields. A daemon continuation observes that termination without extending
the caller's 15-second budget or blocking the server thread. Partial startup with
no admitted native writer releases after successful cleanup and retirement of
its created executors.

An exceptional or cancelled preparation/publication stage is different from an
owner-drain acknowledgement: it can settle while older admitted native writes
still run. Existing adapters cannot prove complete drain after that outcome, so
the opaque token is conservatively retained until JVM restart, even if another
final save subsequently succeeds or the observed executors later exit. The
successor continues to fail closed. A pending successful save or preparation may
release when it genuinely completes; a timeout warning alone is not failure or
permission to discard the token. No manual token removal is a supported recovery
path while the JVM may still contain predecessor writers. Completion-stage
adapters must include all earlier admitted writes in their successful publication
boundary and may not report mere task submission as completed publication.

## What this does not port yet

This is the executor/lifecycle slice of the future shared runtime, not a complete
Bukkit-free AdvancedCore service graph. Users, settings, storage, rewards, game
operations and integration providers are extracted in subsequent changes. Native
packaging must only include dependency-clean classes once that graph is ready;
this PR does not tell a Fabric server to load the full existing AdvancedCore JAR.

## Validation

Use the existing `mvn -B -f AdvancedCore/pom.xml package` command. Existing Bukkit
lifecycle tests remain in place. New tests compare the cleanup/executor ordering,
timeouts, failure handling, object identity and interrupt behavior. A headless
fixture creates real executor owners, runs a task, shuts down and checks rejection
of new work while Bukkit/JUnit are absent from its isolated classpath.

No live-server or downstream VotingPlugin validation is implied by those tests.
