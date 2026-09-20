---
name: rewards
description: Reward execution and persisted queue provenance.
triggers: [reward, queue, generated, snapshot]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4VJTMDNFXVDRE3SS1A
  type: component
  status: promoted
  revision: 1
  title: Reward queue boundary
---

# Reward boundaries

`RewardExecutor` initializes online state, schedules reward execution off the Bukkit primary thread when needed, and resolves persisted queue references at delivery time. Explicit queue references distinguish generated files from normal named rewards; older entries first prefer a registered reward or direct handle, then try generated resolution. This preserves legacy queue provenance and current YAML-defined reward lookup. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/rewards/RewardExecutor.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/user/PersistedQueueReference.java`.

`SharedRewardOrchestrator` sequences a plan through requirements, durable decision/progress, deferral, native action, and checkpoint callbacks. The caller supplies `SharedRewardDurability` and platform actions. The action completes before the progress checkpoint; without an idempotent native action/adapter, a crash in that window can replay it. Do not infer exactly-once delivery from the orchestration interface. The legacy offline queue now claims occurrences, serializes per-user replay, and retains/restores failed entries; inspect its checkpoint and acknowledgement paths separately. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/reward/SharedRewardOrchestrator.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/reward/SharedRewardDurability.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/user/AdvancedCoreUser.java`.
