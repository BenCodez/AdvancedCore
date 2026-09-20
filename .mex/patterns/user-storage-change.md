---
name: user-storage-change
description: Check cache publication against persistence behavior.
triggers: [storage, cache, SQL]
last_updated: 2026-09-20
mex:
  id: mx_01M30783ZG1EDMVDQEA2XMH875
  type: pattern
  status: promoted
  revision: 1
  title: user-storage-change
---

# User storage change

Trace ordinary cache mutation/flush separately from `withAtomicUserTransaction`, including SQL failure, retry, cache reconciliation, and shutdown. Caller-owned records can share the explicit JDBC transaction, but uniqueness and retry remain caller responsibilities. See `context/storage.md` and `AdvancedCore/src/test/java/com/bencodez/advancedcore/tests/user/`.
