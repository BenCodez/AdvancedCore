package com.bencodez.advancedcore.core.reward;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.bencodez.advancedcore.core.reward.SharedRewardRequirement.Outcome;

/**
 * Sequences already-configured reward work without Bukkit dependencies. Native
 * actions remain in platform adapters, while durability remains owned by the
 * injected replay adapter.
 */
public final class SharedRewardOrchestrator {
    private final SharedRewardPlatform platform;

    public SharedRewardOrchestrator(SharedRewardPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    public CompletionStage<SharedRewardResult> execute(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        SharedRewardDurability replay = durability == null ? SharedRewardDurability.NONE : durability;
        return execute(plan, context, replay, pathSegment(plan.id()), null);
    }

    /**
     * Execute a prepared plan for one stable logical occurrence. The caller-owned
     * durability adapter must atomically claim each native action before it runs;
     * an uncertain earlier attempt fails closed for reconciliation. Native steps
     * must be flattened; keyed nested composite plans are not supported.
     */
    public CompletionStage<SharedRewardResult> executeKeyed(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardKeyedDurability durability, String occurrenceKey) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(durability, "durability");
        Objects.requireNonNull(occurrenceKey, "occurrenceKey");
        if (occurrenceKey.isBlank() || occurrenceKey.length() > 256) {
            throw new IllegalArgumentException("Occurrence key must contain 1..256 characters");
        }
        if (!durability.durable()) throw new IllegalArgumentException("Keyed rewards require durable action admission");
        context.markKeyedExecution();
        return execute(plan, context, durability, pathSegment(occurrenceKey) + "/" + pathSegment(plan.id()), durability);
    }

    public CompletionStage<SharedRewardResult> executeNested(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String parentPath) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(parentPath, "parentPath");
        String segment = pathSegment(plan.id());
        String path = parentPath.isBlank() ? segment : parentPath + "/" + segment;
        SharedRewardDurability replay = durability == null ? SharedRewardDurability.NONE : durability;
        if (context.isKeyedExecution()) {
            return failed("Keyed nested plans require an explicit composite-step contract; flatten native steps instead");
        }
        return execute(plan, context, replay, path, null);
    }

    private CompletionStage<SharedRewardResult> execute(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, SharedRewardKeyedDurability keyed) {
        try {
            if (platform.isShuttingDown()) return failed("Reward platform is shutting down");
            String fingerprint = durability.durable() ? plan.fingerprint() : "non-durable";
            SharedRewardProgress saved = durability.durable() ? durability.loadProgress(executionPath) : null;
            if (saved != null) {
                validateProgress(plan, fingerprint, saved, executionPath);
                return continueFromProgress(plan, context, durability, executionPath, fingerprint, saved, keyed);
            }

            int legacyCursor = durability.completedSteps(executionPath);
            if (legacyCursor < 0 || legacyCursor > plan.steps().size()) {
                return failed("Invalid durable reward cursor " + legacyCursor + " for " + executionPath);
            }
            if (durability.durable() && legacyCursor != 0) {
                return failed("Cannot resume an unversioned reward cursor: " + executionPath);
            }

            CompletionStage<Outcome> requirements = legacyCursor > 0
                    ? CompletableFuture.completedFuture(Outcome.PASS)
                    : evaluateRequirements(plan.requirements(), context);
            return requirements.thenCompose(outcome -> {
                if (outcome == Outcome.RETRY) {
                    if (!durability.durable()) return failed("Retryable reward requirement needs durable deferral: " + executionPath);
                    CompletionStage<Void> deferred;
                    try {
                        deferred = durability.defer(executionPath, legacyCursor, context);
                        if (deferred == null) return CompletableFuture.failedFuture(
                                new IllegalStateException("Reward durability adapter returned null requirement deferral stage"));
                    } catch (Throwable failure) {
                        return CompletableFuture.failedFuture(failure);
                    }
                    return deferred.thenApply(ignored -> SharedRewardResult.DEFERRED);
                }

                boolean passed = outcome == Outcome.PASS
                        && (plan.chance() >= 1.0 || platform.nextChanceRoll() < plan.chance());
                SharedRewardProgress decision = new SharedRewardProgress(fingerprint, passed, legacyCursor,
                        context.placeholders(), passed ? platform.now().plus(plan.delay()) : platform.now());
                CompletionStage<SharedRewardProgress> persisted;
                try {
                    persisted = durability.begin(executionPath, decision);
                    if (persisted == null) return CompletableFuture.failedFuture(
                            new IllegalStateException("Reward durability adapter returned null begin stage"));
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
                return persisted.thenCompose(progress -> {
                    validateProgress(plan, fingerprint, progress, executionPath);
                    return continueFromProgress(plan, context, durability, executionPath, fingerprint, progress, keyed);
                });
            });
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<SharedRewardResult> continueFromProgress(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, SharedRewardProgress progress,
            SharedRewardKeyedDurability keyed) {
        context.placeholders().clear();
        context.placeholders().putAll(progress.placeholders());
        if (!progress.eligible()) return CompletableFuture.completedFuture(SharedRewardResult.NOT_ELIGIBLE);
        return executeEligible(plan, context, durability, executionPath, fingerprint, progress, keyed);
    }

    private void validateProgress(SharedRewardPlan plan, String fingerprint, SharedRewardProgress progress,
            String executionPath) {
        if (progress == null || !fingerprint.equals(progress.planFingerprint())) {
            throw new IllegalStateException("Reward plan changed or has no durable binding: " + executionPath);
        }
        if (progress.eligible() && progress.completedSteps() == 0 && !plan.delay().isZero()
                && progress.notBefore() == null) {
            throw new IllegalStateException("Durable reward progress has no delay deadline: " + executionPath);
        }
        if (progress.completedSteps() > plan.steps().size()) {
            throw new IllegalStateException("Invalid durable reward cursor for " + executionPath);
        }
    }

    private CompletionStage<SharedRewardResult> executeEligible(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, SharedRewardProgress progress,
            SharedRewardKeyedDurability keyed) {
        int resume = progress.completedSteps();
        java.util.function.Supplier<CompletionStage<SharedRewardResult>> work =
                () -> executeSteps(plan, context, durability, executionPath, fingerprint, resume, keyed);
        Duration delay = Duration.ZERO;
        if (resume == 0 && !plan.delay().isZero()) {
            delay = durability.durable() ? Duration.between(platform.now(), progress.notBefore()) : plan.delay();
            if (delay.isNegative()) delay = Duration.ZERO;
        }
        if (delay.isZero()) return work.get();
        try {
            CompletionStage<SharedRewardResult> delayed = platform.delay(delay, work);
            return delayed == null ? failed("Reward platform returned null delay stage") : delayed;
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Outcome> evaluateRequirements(List<SharedRewardRequirement> requirements,
            SharedRewardContext context) {
        CompletionStage<Outcome> chain = CompletableFuture.completedFuture(Outcome.PASS);
        for (SharedRewardRequirement requirement : requirements) {
            chain = chain.thenCompose(previous -> {
                if (previous != Outcome.PASS) return CompletableFuture.completedFuture(previous);
                try {
                    CompletionStage<Outcome> stage = requirement.evaluate(context);
                    return stage == null ? CompletableFuture.failedFuture(
                            new IllegalStateException("Reward requirement returned null completion stage")) : stage;
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            });
        }
        return chain.thenApply(outcome -> outcome == null ? Outcome.FAIL : outcome);
    }

    private CompletionStage<SharedRewardResult> executeSteps(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index,
            SharedRewardKeyedDurability keyed) {
        CompletionStage<SharedRewardResult> chain = CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        for (int current = index; current < plan.steps().size(); current++) {
            final int stepIndex = current;
            chain = chain.thenCompose(previous -> previous == SharedRewardResult.DEFERRED
                    ? CompletableFuture.completedFuture(SharedRewardResult.DEFERRED)
                    : executeStep(plan.steps().get(stepIndex), context, durability, executionPath, fingerprint, stepIndex, keyed));
        }
        return chain.thenCompose(result -> result != SharedRewardResult.DEFERRED && platform.isShuttingDown()
                ? failed("Reward platform shut down before execution completed")
                : CompletableFuture.completedFuture(result));
    }

    private CompletionStage<SharedRewardResult> executeStep(SharedRewardStep step, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index,
            SharedRewardKeyedDurability keyed) {
        if (keyed != null) {
            if (!step.requiresOnlinePlayer()) {
                return executeStepOnNative(step, context, durability, executionPath, fingerprint, index, keyed, false);
            }
            try {
                CompletionStage<Boolean> availability = platform.checkActionAvailability(context.userId());
                if (availability == null) return failed("Reward platform returned null availability stage");
                return availability.thenCompose(online -> executeStepOnNative(step, context, durability,
                        executionPath, fingerprint, index, keyed, Boolean.TRUE.equals(online)));
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        return executeStepOnNative(step, context, durability, executionPath, fingerprint, index, null, false);
    }

    private CompletionStage<SharedRewardResult> executeStepOnNative(SharedRewardStep step, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index,
            SharedRewardKeyedDurability keyed, boolean onlineChecked) {
        if (platform.isShuttingDown()) return failed("Reward platform shut down before execution completed");
        if (step.requiresOnlinePlayer() && (keyed != null ? !onlineChecked : !platform.isOnline(context.userId()))) {
            if (!durability.durable()) return failed("Player became unavailable during non-durable reward step " + step.id());
            CompletionStage<Void> deferred;
            try {
                deferred = durability.defer(executionPath, fingerprint, index, context);
                if (deferred == null) return CompletableFuture.failedFuture(
                        new IllegalStateException("Reward durability adapter returned null deferral stage"));
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return deferred.thenApply(ignored -> SharedRewardResult.DEFERRED);
        }

        String stepPath = executionPath + "/" + pathSegment(step.id()) + ":" + index;
        if (keyed == null) return executeClaimedStep(step, context, durability, executionPath, fingerprint, index, stepPath, false);
        CompletionStage<SharedRewardActionClaim> claim;
        try {
            claim = keyed.claimAction(executionPath, fingerprint, index, context);
            if (claim == null) return failed("Keyed reward adapter returned null action claim stage");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return claim.thenCompose(result -> {
            if (result == SharedRewardActionClaim.INDETERMINATE) {
                return CompletableFuture.failedFuture(new SharedRewardIndeterminateException(executionPath, index));
            }
            if (result != SharedRewardActionClaim.STARTED) return failed("Invalid keyed reward action claim");
            try {
                CompletionStage<SharedRewardResult> dispatched = platform.runClaimedAction(context.userId(),
                        step.requiresOnlinePlayer(), () -> {
                    // Admission may have awaited storage while the server disabled
                    // or the player disconnected. Leave the claim for reconciliation.
                    if (platform.isShuttingDown()
                            || (step.requiresOnlinePlayer() && !platform.isOnline(context.userId()))) {
                        return CompletableFuture.failedFuture(
                                new SharedRewardIndeterminateException(executionPath, index));
                    }
                    return executeClaimedStep(step, context, durability, executionPath, fingerprint, index,
                            stepPath, true);
                });
                return dispatched == null ? failed("Reward platform returned null claimed action stage") : dispatched;
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        });
    }

    private CompletionStage<SharedRewardResult> executeClaimedStep(SharedRewardStep step, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index, String stepPath,
            boolean claimedStrict) {
        CompletionStage<SharedRewardResult> action;
        try {
            action = step.action().execute(context, stepPath);
            if (action == null) return CompletableFuture.failedFuture(
                    new IllegalStateException("Reward step returned null completion stage: " + step.id()));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }

        return action.thenCompose(result -> {
            if (result == null) return CompletableFuture.failedFuture(
                    new IllegalStateException("Reward step returned null result: " + step.id()));
            if (result == SharedRewardResult.DEFERRED) {
                if (claimedStrict) {
                    return failed("Keyed reward action deferred after it was claimed: " + step.id());
                }
                return CompletableFuture.completedFuture(SharedRewardResult.DEFERRED);
            }
            CompletionStage<Void> checkpoint;
            try {
                checkpoint = durability.checkpoint(executionPath, fingerprint, index + 1, context);
                if (checkpoint == null) return CompletableFuture.failedFuture(
                        new IllegalStateException("Reward durability adapter returned null checkpoint stage"));
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return checkpoint.thenApply(ignored -> SharedRewardResult.COMPLETED);
        });
    }

    private static String pathSegment(String id) {
        if (id.indexOf('/') < 0 && id.indexOf(':') < 0 && id.indexOf('%') < 0) return id;
        return "%" + Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private CompletionStage<SharedRewardResult> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
