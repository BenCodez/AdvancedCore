---
name: decisions
description: Compatibility constraints for downstream consumers.
triggers: [decision, compatibility, migration]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4VNVXE8YM59FVJJCNG
  type: decision
  status: promoted
  revision: 1
  title: AdvancedCore compatibility decisions
---

# Compatibility decisions

AdvancedCore is shaded/consumed by VotingPlugin. Public user/reward changes affect its vote path even when this repository's own tests pass; check the current consumer source and dependency version before changing a contract. Source: `AdvancedCore/pom.xml`, root `AGENTS.md`.

New queued reward references encode normal versus generated provenance. Legacy bare names have no marker: resolver precedence favors a registered/direct reward before generated fallback when names collide. This compatibility behavior does not establish exactly-once execution. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/rewards/RewardExecutor.java`, `AdvancedCore/src/test/java/com/bencodez/advancedcore/tests/rewards/QueuedGeneratedRewardDispatchTest.java`.
