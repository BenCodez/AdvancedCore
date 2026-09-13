package com.bencodez.advancedcore.core.reward;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        return execute(plan, context, replay, plan.id());
    }

    public CompletionStage<SharedRewardResult> executeNested(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String parentPath) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(parentPath, "parentPath");
        String path = parentPath.isBlank() ? plan.id() : parentPath + "/" + plan.id();
        return execute(plan, context, durability == null ? SharedRewardDurability.NONE : durability, path);
    }

    private CompletionStage<SharedRewardResult> execute(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath) {
        try {
            if (platform.isShuttingDown()) return failed("Reward platform is shutting down");
            String fingerprint = durability.durable() ? plan.fingerprint() : "non-durable";
            SharedRewardProgress saved = durability.durable() ? durability.loadProgress(executionPath) : null;
            CompletionStage<SharedRewardProgress> started;
            if (saved != null) {
                validateProgress(plan, fingerprint, saved, executionPath);
                started = CompletableFuture.completedFuture(saved);
            } else {
                int legacyCursor = durability.completedSteps(executionPath);
                if (legacyCursor < 0 || legacyCursor > plan.steps().size()) {
                    return failed("Invalid durable reward cursor " + legacyCursor + " for " + executionPath);
                }
                if (durability.durable() && legacyCursor != 0) {
                    return failed("Cannot resume an unversioned reward cursor: " + executionPath);
                }
                CompletionStage<Boolean> eligible = legacyCursor > 0
                        ? CompletableFuture.completedFuture(Boolean.TRUE)
                        : evaluateRequirements(plan.requirements(), context).thenApply(passed ->
                                passed.booleanValue() && (plan.chance() >= 1.0
                                        || platform.nextChanceRoll() < plan.chance()));
                started = eligible.thenCompose(passed -> {
                    SharedRewardProgress decision = new SharedRewardProgress(fingerprint, passed.booleanValue(),
                            legacyCursor, context.placeholders(),
                            passed.booleanValue() ? platform.now().plus(plan.delay()) : platform.now());
                    CompletionStage<SharedRewardProgress> persisted = durability.begin(executionPath, decision);
                    return persisted == null ? CompletableFuture.failedFuture(
                            new IllegalStateException("Reward durability adapter returned null begin stage")) : persisted;
                });
            }
            return started.thenCompose(progress -> {
                validateProgress(plan, fingerprint, progress, executionPath);
                context.placeholders().clear();
                context.placeholders().putAll(progress.placeholders());
                if (!progress.eligible()) return CompletableFuture.completedFuture(SharedRewardResult.NOT_ELIGIBLE);
                return executeEligible(plan, context, durability, executionPath, fingerprint, progress);
            });
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
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
            SharedRewardDurability durability, String executionPath, String fingerprint, SharedRewardProgress progress) {
        int resume = progress.completedSteps();
        java.util.function.Supplier<CompletionStage<SharedRewardResult>> work =
                () -> executeSteps(plan, context, durability, executionPath, fingerprint, resume);
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

    /** Build requirement sequencing iteratively so completed stages cannot recurse through the JVM stack. */
    private CompletionStage<Boolean> evaluateRequirements(List<SharedRewardRequirement> requirements,
            SharedRewardContext context) {
        CompletionStage<Boolean> chain = CompletableFuture.completedFuture(Boolean.TRUE);
        for (SharedRewardRequirement requirement : requirements) {
            chain = chain.thenCompose(passed -> {
                if (!Boolean.TRUE.equals(passed)) return CompletableFuture.completedFuture(Boolean.FALSE);
                try {
                    CompletionStage<Boolean> stage = requirement.test(context);
                    return stage == null ? CompletableFuture.failedFuture(
                            new IllegalStateException("Reward requirement returned null completion stage")) : stage;
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            });
        }
        // A null Boolean is a failed requirement, including in the final slot.
        return chain.thenApply(Boolean.TRUE::equals);
    }

    /**
     * Build the step chain iteratively. Already-completed action/checkpoint stages may
     * run inline, but no step invokes the next step recursively, so large synchronous
     * plans remain stack-safe.
     */
    private CompletionStage<SharedRewardResult> executeSteps(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index) {
        CompletionStage<SharedRewardResult> chain = CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        for (int current = index; current < plan.steps().size(); current++) {
            final int stepIndex = current;
            chain = chain.thenCompose(previous -> previous == SharedRewardResult.DEFERRED
                    ? CompletableFuture.completedFuture(SharedRewardResult.DEFERRED)
                    : executeStep(plan.steps().get(stepIndex), context, durability,
                            executionPath, fingerprint, stepIndex));
        }
        // Preserve the old terminal shutdown check after the final checkpoint,
        // including empty plans and fully checkpointed resumes. Deferrals stay deferred.
        return chain.thenCompose(result -> result != SharedRewardResult.DEFERRED && platform.isShuttingDown()
                ? failed("Reward platform shut down before execution completed")
                : CompletableFuture.completedFuture(result));
    }

    private CompletionStage<SharedRewardResult> executeStep(SharedRewardStep step, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, String fingerprint, int index) {
        if (platform.isShuttingDown()) {
            return failed("Reward platform shut down before execution completed");
        }
        if (step.requiresOnlinePlayer() && !platform.isOnline(context.userId())) {
            if (!durability.durable()) {
                return failed("Player became unavailable during non-durable reward step " + step.id());
            }
            CompletionStage<Void> deferred;
            try {
                deferred = durability.defer(executionPath, fingerprint, index, context);
                if (deferred == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Reward durability adapter returned null deferral stage"));
                }
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return deferred.thenApply(ignored -> SharedRewardResult.DEFERRED);
        }

        String stepPath = executionPath + "/" + step.id() + ":" + index;
        CompletionStage<SharedRewardResult> action;
        try {
            action = step.action().execute(context, stepPath);
            if (action == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Reward step returned null completion stage: " + step.id()));
            }
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }

        return action.thenCompose(result -> {
            if (result == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Reward step returned null result: " + step.id()));
            }
            if (result == SharedRewardResult.DEFERRED) {
                return CompletableFuture.completedFuture(SharedRewardResult.DEFERRED);
            }
            CompletionStage<Void> checkpoint;
            try {
                checkpoint = durability.checkpoint(executionPath, fingerprint, index + 1, context);
                if (checkpoint == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Reward durability adapter returned null checkpoint stage"));
                }
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return checkpoint.thenApply(ignored -> SharedRewardResult.COMPLETED);
        });
    }

    private CompletionStage<SharedRewardResult> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
