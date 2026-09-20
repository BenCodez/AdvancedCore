---
name: storage
description: Current user cache and persistence semantics and limits.
triggers: [storage, cache, SQL, persistence]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4V8D4NK2BM9GH4VY2R
  type: component
  status: promoted
  revision: 1
  title: User and storage boundary
---

# User and storage boundary

Ordinary cached writes and the explicit atomic transaction are distinct paths. `UserDataCache.addChange` can publish before its queued persistence; a suppressed storage exception must not be mistaken for a durable acknowledgement. For caller-owned SQL records, `UserDataManager.withAtomicUserTransaction` enters the shared runtime, flushes queued cache work, and reconciles the committed snapshot. Initial values are row prerequisites; operation-specific mutations belong inside the callback. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/user/usercache/UserDataCache.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/api/user/usercache/UserDataManager.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/user/runtime/SharedUserDataRuntime.java`.

`SqlUserStorage.transaction` exposes a bounded callback with a locked user row and one backend-owned JDBC connection. The scope can read/write user values and access caller-owned SQL tables within the same commit; callers must not commit, close, change auto-commit, or retain the connection. This boundary does not supply a unique operation key, retry policy, or reward acknowledgement by itself. Blocking use belongs on a storage worker. Source: `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/user/storage/SqlUserStorage.java`, `AdvancedCore/src/main/java/com/bencodez/advancedcore/core/user/storage/sql/JdbcSqlUserStorage.java`.
