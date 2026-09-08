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
- JavaScript cleanup, MySQL closing and server timestamp happen before executor
  shutdown, as before. This extraction does not redesign database flushing.
- Executor grace periods remain 2/2/2/1 seconds, followed by reward shutdown,
  forced executor shutdown and the existing unload/cache/inventory/integration
  cleanup order. The time-checker timer is captured at the same point.
- Individual component failures retain the warning/debug callback behavior and
  do not skip later components. Interrupted waits restore the interrupt flag.
- Lifecycle calls remain serialized by the owning platform. Hook execution is
  synchronous; the executor grace periods are not a global bound on all cleanup
  callbacks. No stronger transactional or idempotency guarantee is claimed.

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
