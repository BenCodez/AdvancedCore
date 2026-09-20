---
name: architecture
description: AdvancedCore library ownership boundaries.
triggers: [architecture, API, user, reward]
last_updated: 2026-09-20
mex:
  id: mx_01M30783X7XRSR3B5HENZVSXAJ
  type: architecture
  status: promoted
  revision: 1
  title: architecture
---

# Library boundaries

AdvancedCore provides user data/cache, reward construction and dispatch, compatibility helpers, and platform integrations consumed by plugins including VotingPlugin. Legacy/public surfaces remain in `api/user` and `api/rewards`; newer shared contracts live under `core/user` and `core/reward`. Those shared contracts coexist with Bukkit adapters rather than replacing every legacy path. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/user/`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/user/`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/reward/`.

Bukkit-specific reward dispatch and scheduling still exist. The shared orchestrator sequences configured work through injected platform and durability adapters; inspect the actual adapter before assuming a stronger delivery guarantee. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/rewards/RewardExecutor.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/reward/SharedRewardOrchestrator.java`.
