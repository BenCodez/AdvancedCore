package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class RewardAsyncInjectionTest {

	@TempDir
	File tempDir;

	private AdvancedCorePlugin plugin;
	private RewardHandler handler;
	private Reward reward;
	private ConfigurationSection data;
	private AdvancedCoreUser user;
	private AtomicBoolean enabled;
	private com.bencodez.simpleapi.scheduler.BukkitScheduler scheduler;

	@BeforeEach
	void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		enabled = new AtomicBoolean(true);
		when(plugin.isEnabled()).thenAnswer(ignored -> enabled.get());
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		doAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AdvancedCorePlugin.setInstance(plugin);
		handler = new RewardHandler(plugin);
		when(plugin.getRewardHandler()).thenReturn(handler);

		YamlConfiguration configuration = new YamlConfiguration();
		data = configuration.createSection("Reward");
		reward = new Reward("AsyncReward", data);
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(reward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		user = mock(AdvancedCoreUser.class);
	}

	@AfterEach
	void tearDown() {
		handler.getDelayedTimer().shutdownNow();
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	void asyncInjectionsAreSequentialAndPostRewardsWaitForThem() {
		List<String> events = new ArrayList<>();
		HashMap<String, String> placeholders = new HashMap<>();
		CompletableFuture<Object> deferred = new CompletableFuture<>();

		RewardInject first = new RewardInject("First") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous hook should be used");
			}

			@Override
			public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("first-start");
				return deferred;
			}
		};
		first.asPlaceholder("first");
		RewardInject second = new RewardInject("Second") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous hook should be used");
			}

			@Override
			public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("second:" + ignoredPlaceholders.get("first"));
				return CompletableFuture.completedFuture("second-value");
			}
		};
		second.asPlaceholder("second");
		RewardInject post = new RewardInject("Post") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, ConfigurationSection ignoredData,
					HashMap<String, String> ignoredPlaceholders) {
				events.add("post:" + ignoredPlaceholders.get("second"));
				return null;
			}
		};
		post.postReward();
		handler.getInjectedRewards().add(first);
		handler.getInjectedRewards().add(second);
		handler.getInjectedRewards().add(post);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, placeholders);
		assertFalse(result.toCompletableFuture().isDone());
		assertEquals(List.of("first-start"), events);

		deferred.complete("first-value");
		result.toCompletableFuture().join();
		assertEquals(List.of("first-start", "second:first-value", "post:second-value"), events);
		assertEquals("first-value", placeholders.get("first"));
		assertEquals("second-value", placeholders.get("second"));
	}

	@Test
	void typedIntegerAsyncHookUsesParsedValue() {
		data.set("Amount", 7);
		HashMap<String, String> placeholders = new HashMap<>();
		List<Integer> received = new ArrayList<>();

		RewardInjectInt amount = new RewardInjectInt("Amount") {
			@Override
			public boolean supportsAsyncRequest() {
				return true;
			}

			@Override
			public String onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser, int ignoredValue,
					HashMap<String, String> ignoredPlaceholders) {
				throw new AssertionError("the asynchronous typed hook should be used");
			}

			@Override
			public CompletionStage<String> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser, int value,
					HashMap<String, String> ignoredPlaceholders) {
				received.add(value);
				return CompletableFuture.completedFuture("async-" + value);
			}
		};
		amount.asPlaceholder("amount");
		handler.getInjectedRewards().add(amount);

		reward.giveInjectedRewardsAsync(user, placeholders).toCompletableFuture().join();
		assertTrue(received.contains(7));
		assertEquals("async-7", placeholders.get("amount"));
	}

	@Test
	void failedAsyncInjectionStopsDependentRewardsAndPropagates() {
		AtomicBoolean dependentRan = new AtomicBoolean();
		RewardInject failing = asyncInjection("Failure", CompletableFuture.failedFuture(
				new IllegalStateException("durable update failed")), null);
		RewardInject dependent = asyncInjection("Dependent", CompletableFuture.completedFuture(null), dependentRan);
		handler.getInjectedRewards().add(failing);
		handler.getInjectedRewards().add(dependent);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		assertFalse(dependentRan.get());
	}

	@Test
	void synchronizedAsyncInjectionSerializesThroughCompletion() {
		List<CompletableFuture<Object>> completions = new ArrayList<>();
		List<Integer> starts = new ArrayList<>();
		RewardInject serialized = new RewardInject("Serialized") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				starts.add(starts.size() + 1);
				CompletableFuture<Object> completion = new CompletableFuture<>();
				completions.add(completion);
				return completion;
			}
		};
		serialized.synchronize();
		handler.getInjectedRewards().add(serialized);

		CompletionStage<Void> first = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		CompletionStage<Void> second = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertEquals(List.of(1), starts);

		completions.get(0).complete(null);
		assertEquals(List.of(1, 2), starts);
		completions.get(1).complete(null);
		first.toCompletableFuture().join();
		second.toCompletableFuture().join();
	}

	@Test
	void shutdownStopsContinuationsAfterPendingInjection() {
		CompletableFuture<Object> completion = new CompletableFuture<>();
		AtomicBoolean dependentRan = new AtomicBoolean();
		handler.getInjectedRewards().add(asyncInjection("Pending", completion, null));
		handler.getInjectedRewards().add(
				asyncInjection("Dependent", CompletableFuture.completedFuture(null), dependentRan));
		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());

		enabled.set(false);
		completion.complete(null);

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		assertFalse(dependentRan.get());
	}

	@Test
	void schedulerCancellationCompletesStageExceptionally() {
		Reward shortTimeoutReward = new Reward("AsyncReward", data) {
			@Override
			protected long getServerThreadDispatchTimeoutMillis() {
				return 25;
			}
		};
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(shortTimeoutReward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		AtomicBoolean invoked = new AtomicBoolean();
		handler.getInjectedRewards().add(
				asyncInjection("Dropped", CompletableFuture.completedFuture(null), invoked));
		org.mockito.Mockito.reset(scheduler);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AtomicReference<Runnable> droppedTask = new AtomicReference<>();
		doAnswer(invocation -> {
			droppedTask.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));

		CompletionStage<Void> result = shortTimeoutReward.giveInjectedRewardsAsync(user, new HashMap<>());

		assertThrows(java.util.concurrent.CompletionException.class, () -> result.toCompletableFuture().join());
		droppedTask.get().run();
		assertFalse(invoked.get());
	}

	@Test
	void injectionCompletionIsNotLimitedBySchedulerHandoffTimeout() throws Exception {
		Reward shortTimeoutReward = new Reward("AsyncReward", data) {
			@Override
			protected long getServerThreadDispatchTimeoutMillis() {
				return 25;
			}
		};
		try {
			java.lang.reflect.Field pluginField = Reward.class.getDeclaredField("plugin");
			pluginField.setAccessible(true);
			pluginField.set(shortTimeoutReward, plugin);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
		CompletableFuture<Object> completion = new CompletableFuture<>();
		handler.getInjectedRewards().add(asyncInjection("SlowStorage", completion, null));

		CompletionStage<Void> result = shortTimeoutReward.giveInjectedRewardsAsync(user, new HashMap<>());
		Thread.sleep(75);
		assertFalse(result.toCompletableFuture().isDone());
		completion.complete(null);
		result.toCompletableFuture().join();
	}

	private RewardInject asyncInjection(String path, CompletionStage<Object> completion, AtomicBoolean invoked) {
		return new RewardInject(path) {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
					ConfigurationSection data, HashMap<String, String> placeholders) {
				if (invoked != null) invoked.set(true);
				return completion;
			}
		};
	}
}
