---
name: router
description: AdvancedCore durable knowledge routes.
edges:
  - target: patterns/user-storage-change.md
    condition: when changing user persistence
  - target: patterns/reward-queue-change.md
    condition: when changing queued reward resolution
last_updated: 2026-09-20
---

# AdvancedCore memory routes

| Task | Read |
| --- | --- |
| Shared API/platform ownership | `context/architecture.md`, `context/stack.md` |
| User/cache/SQL persistence | `context/storage.md`, `patterns/user-storage-change.md` |
| Rewards or queue restoration | `context/rewards.md`, `patterns/reward-queue-change.md` |
| Why current limitations matter | `context/decisions.md` |
| Build or MEX environment | `context/setup.md` |

Root `AGENTS.md` owns authority and use guidance. Inspect current Java/tests after relevant MEX retrieval.
