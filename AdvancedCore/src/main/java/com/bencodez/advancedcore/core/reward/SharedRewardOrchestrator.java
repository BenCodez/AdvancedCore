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
        Objects.requireNonNull(parentPath, "parentPath");
        String path = parentPath.isBlank() ? plan.id() : parentPath + "/" + plan.id();
        return execute(plan, context, durability == null ? SharedRewardDurability.NONE : durability, path);
    }

    private CompletionStage<SharedRewardResult> execute(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath) {
        if (platform.isShuttingDown()) {
            return failed("Reward platform is shutting down");
        }

        int resume = durability.completedSteps(executionPath);
        if (resume < 0 || resume > plan.steps().size()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Invalid durable reward cursor " + resume + " for " + executionPath));
        }

        CompletionStage<Boolean> eligible = resume > 0
                ? CompletableFuture.completedFuture(Boolean.TRUE)
                : evaluateRequirements(plan.requirements(), context, 0);
        return eligible.thenCompose(requirementsPassed -> {
            if (!requirementsPassed.booleanValue()) {
                return CompletableFuture.completedFuture(SharedRewardResult.NOT_ELIGIBLE);
            }
            if (resume == 0 && plan.chance() < 1.0 && platform.nextChanceRoll() >= plan.chance()) {
                return CompletableFuture.completedFuture(SharedRewardResult.NOT_ELIGIBLE);
            }

            java.util.function.Supplier<CompletionStage<SharedRewardResult>> work =
                    () -> executeSteps(plan, context, durability, executionPath, resume);
            Duration delay = resume == 0 ? plan.delay() : Duration.ZERO;
            if (delay.isZero()) {
                return work.get();
            }
            try {
                CompletionStage<SharedRewardResult> delayed = platform.delay(delay, work);
                if (delayed == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Reward platform returned null delay stage"));
                }
                return delayed;
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        });
    }

    private CompletionStage<Boolean> evaluateRequirements(List<SharedRewardRequirement> requirements,
            SharedRewardContext context, int index) {
        if (index >= requirements.size()) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        CompletionStage<Boolean> stage;
        try {
            stage = requirements.get(index).test(context);
            if (stage == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Reward requirement returned null completion stage"));
            }
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return stage.thenCompose(passed -> Boolean.TRUE.equals(passed)
                ? evaluateRequirements(requirements, context, index + 1)
                : CompletableFuture.completedFuture(Boolean.FALSE));
    }

    private CompletionStage<SharedRewardResult> executeSteps(SharedRewardPlan plan, SharedRewardContext context,
            SharedRewardDurability durability, String executionPath, int index) {
        if (platform.isShuttingDown()) {
            return failed("Reward platform shut down before execution completed");
        }
        if (index >= plan.steps().size()) {
            return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        }

        SharedRewardStep step = plan.steps().get(index);
        if (step.requiresOnlinePlayer() && !platform.isOnline(context.userId())) {
            if (!durability.durable()) {
                return failed("Player became unavailable during non-durable reward step " + step.id());
            }
            CompletionStage<Void> deferred;
            try {
                deferred = durability.defer(executionPath, index, context);
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
                checkpoint = durability.checkpoint(executionPath, index + 1, context);
                if (checkpoint == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Reward durability adapter returned null checkpoint stage"));
                }
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return checkpoint.thenCompose(ignored -> executeSteps(plan, context, durability, executionPath, index + 1));
        });
    }

    private CompletionStage<SharedRewardResult> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
