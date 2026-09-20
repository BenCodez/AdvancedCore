---
name: reward-queue-change
description: Preserve normal/generated reward provenance on queue delivery.
triggers: [reward, queue, generated]
last_updated: 2026-09-20
mex:
  id: mx_01M30783Z5PYF08WAWW4ANAERJ
  type: pattern
  status: promoted
  revision: 1
  title: reward-queue-change
---

# Queued reward change

Trace how the queue reference is written, restored, resolved, and dispatched. Test normal named, generated-file, legacy bare-name, and registered/generated name-collision cases. Include occurrence claims, checkpoint persistence, and failure recovery when changing replay. Keep the delivery claim narrower than the actual action and checkpoint contract. See `context/rewards.md` and `AdvancedCore/src/test/java/com/bencodez/advancedcore/tests/rewards/QueuedGeneratedRewardDispatchTest.java`.
