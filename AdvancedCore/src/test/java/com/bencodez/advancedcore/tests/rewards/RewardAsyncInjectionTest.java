package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.rewards.builtin.RewardSubRewards;
import com.bencodez.advancedcore.api.rewards.builtin.RewardRandomReward;
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
	void checkpointWriteRunsOffServerThreadAndGatesTheNextInjection() throws Exception {
		ScheduledExecutorService storageExecutor = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(storageExecutor);
		List<String> events = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("First") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("first");
				return null;
			}
		});
		handler.getInjectedRewards().add(new RewardInject("Second") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("second");
				return null;
			}
		});

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		constructor.setAccessible(true);
		Object replayState = constructor.newInstance(null, null, false);
		java.lang.reflect.Method setConsumer = stateType.getDeclaredMethod("setCheckpointConsumer",
				java.util.function.Consumer.class);
		setConsumer.setAccessible(true);
		setConsumer.invoke(replayState, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint ->
				events.add("checkpoint:" + checkpoint.getReplayProgress().get("AsyncReward")));
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		@SuppressWarnings("unchecked")
		CompletionStage<Void> result = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				replayState, "AsyncReward", "occurrence");

		ArgumentCaptor<Runnable> writes = ArgumentCaptor.forClass(Runnable.class);
		verify(storageExecutor).execute(writes.capture());
		assertEquals(List.of("first"), events);
		assertFalse(result.toCompletableFuture().isDone());

		writes.getValue().run();
		verify(storageExecutor, times(2)).execute(writes.capture());
		assertEquals(List.of("first", "checkpoint:1", "second"), events);
		writes.getAllValues().get(writes.getAllValues().size() - 1).run();
		result.toCompletableFuture().join();
		assertEquals(List.of("first", "checkpoint:1", "second", "checkpoint:2"), events);
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
	void failingLegacyInjectionDoesNotStopLaterAsyncChainSteps() {
		AtomicBoolean laterRan = new AtomicBoolean();
		handler.getInjectedRewards().add(new RewardInject("BrokenLegacy") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				throw new IllegalStateException("bad legacy configuration");
			}
		});
		handler.getInjectedRewards().add(asyncInjection("Later", CompletableFuture.completedFuture(null), laterRan));

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		assertTrue(laterRan.get());
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

	@Test
	void nestedPostRewardWaitsForChildRewardBeforeAdvancing() {
		handler = org.mockito.Mockito.spy(new RewardHandler(plugin));
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(user.getPlugin()).thenReturn(plugin);
		data.createSection("Rewards");
		CompletableFuture<Void> child = new CompletableFuture<>();
		doReturn(child).when(handler).giveRewardAsync(eq(user), any(ConfigurationSection.class), eq("Rewards"),
				any());
		List<String> events = new ArrayList<>();
		RewardSubRewards.register(handler, plugin);
		RewardInject after = new RewardInject("After") {
			@Override
			public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("after-child");
				return null;
			}
		};
		after.postReward();
		handler.getInjectedRewards().add(after);

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertFalse(result.toCompletableFuture().isDone());
		assertTrue(events.isEmpty());

		child.complete(null);
		result.toCompletableFuture().join();
		assertEquals(List.of("after-child"), events);
	}

	@Test
	void everyNestedRewardInjectorWaitsForItsSelectedChild() {
		handler = org.mockito.Mockito.spy(new RewardHandler(plugin));
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(user.getPlugin()).thenReturn(plugin);
		data.set("RandomReward", new ArrayList<>(List.of("child")));
		CompletableFuture<Void> child = new CompletableFuture<>();
		doReturn(child).when(handler).giveRewardAsync(eq(user), eq("child"), any());
		List<String> events = new ArrayList<>();
		RewardRandomReward.register(handler, plugin);
		handler.getInjectedRewards().add(new RewardInject("After") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				events.add("after-child");
				return null;
			}
		}.postReward());

		CompletionStage<Void> result = reward.giveInjectedRewardsAsync(user, new HashMap<>());
		assertFalse(result.toCompletableFuture().isDone());
		assertTrue(events.isEmpty());
		child.complete(null);
		result.toCompletableFuture().join();
		assertEquals(List.of("after-child"), events);
	}

	@Test
	void replayCheckpointSkipsAlreadyAppliedInjectionAfterLaterFailure() throws Exception {
		AtomicInteger alreadyApplied = new AtomicInteger();
		AtomicBoolean fail = new AtomicBoolean(true);
		RewardInject applied = new RewardInject("Money") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				alreadyApplied.incrementAndGet();
				return "receipt-1";
			}
		};
		applied.asPlaceholder("receipt");
		handler.getInjectedRewards().add(applied);
		handler.getInjectedRewards().add(new RewardInject("Durable") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return fail.get() ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
						: CompletableFuture.completedFuture(null);
			}
		});
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class);
		replay.setAccessible(true);
		CompletionStage<Void> first = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0);
		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> first.toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertEquals(1, checkpoint.getCompletedInjectionCount());
		assertEquals("receipt-1", checkpoint.getReplayPlaceholders().get("receipt"));
		fail.set(false);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(),
				checkpoint.getCompletedInjectionCount());
		resumed.toCompletableFuture().join();
		assertEquals(1, alreadyApplied.get());
	}

	@Test
	void persistedReplayRejectsAChangedInjectorRegistryBeforeAnyStageRuns() throws Exception {
		AtomicInteger applied = new AtomicInteger();
		AtomicInteger inserted = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Applied") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				applied.incrementAndGet();
				return null;
			}
		});
		handler.getInjectedRewards().add(new RewardInject("Fails") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
			}
		});

		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertFalse(checkpoint.getReplayRegistryFingerprints().isEmpty());

		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> matchingRegistry = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");
		assertThrows(java.util.concurrent.CompletionException.class, () -> matchingRegistry.toCompletableFuture().join());
		assertEquals(1, applied.get());

		handler.getInjectedRewards().add(0, new RewardInject("Inserted") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				inserted.incrementAndGet();
				return null;
			}
		});
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(1, applied.get());
		assertEquals(0, inserted.get());
	}

	@Test
	void legacyCountOnlyReplayCheckpointDoesNotResumeAgainstAnUnknownRegistry() throws Exception {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Applied") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 1,
				state.newInstance(Map.of(), Map.of(), true), "AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(0, invoked.get());
	}

	@Test
	void persistedReplayDoesNotFallBackToSynchronousDispatchWhenAsyncInjectorsAreGone() {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Synchronous") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		RewardOptions options = new RewardOptions();
		options.setAsyncReplayProgress(Map.of("AsyncReward", 1));
		options.setAsyncReplayRegistryFingerprints(Map.of("AsyncReward", "removed-async-injector"));

		reward.giveRewardUser(user, new HashMap<>(), options);

		assertEquals(0, invoked.get());
	}

	@Test
	void sharedNestedReplayStatePreventsSynchronousFallbackWhenOptionMapsAreEmpty() throws Exception {
		AtomicInteger invoked = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Synchronous") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				invoked.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		RewardOptions options = new RewardOptions();
		options.setAsyncReplayKey("AsyncReward/0/child");
		options.setAsyncReplayState((Reward.ReplayState) state.newInstance(Map.of("AsyncReward/0/child", 1),
				Map.of("AsyncReward/0/child", "removed-async-injector"), false));
		assertTrue(options.getAsyncReplayProgress().isEmpty());
		assertTrue(options.getAsyncReplayRegistryFingerprints().isEmpty());

		reward.giveRewardUser(user, new HashMap<>(), options);

		assertEquals(0, invoked.get());
	}

	@Test
	void childCheckpointBindsItsUncompletedParentRegistryBeforeNestedDispatch() throws Exception {
		AtomicBoolean dispatchingChild = new AtomicBoolean();
		AtomicInteger parentInvocations = new AtomicInteger();
		AtomicInteger insertedInvocations = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Nested") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward current, AdvancedCoreUser currentUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				if (dispatchingChild.get()) return CompletableFuture.completedFuture(null);
				parentInvocations.incrementAndGet();
				dispatchingChild.set(true);
				try {
					Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
					java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
							AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
					replay.setAccessible(true);
					CompletionStage<Void> child = (CompletionStage<Void>) replay.invoke(current, currentUser, new HashMap<>(), 0,
							Reward.currentReplayState(), Reward.currentReplayKey() + "/child");
					return child.whenComplete((ignored, failure) -> dispatchingChild.set(false)).thenApply(ignored -> null);
				} catch (ReflectiveOperationException failure) {
					return CompletableFuture.failedFuture(failure);
				}
			}
		});
		handler.getInjectedRewards().add(new RewardInject("ChildFailure") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				return dispatchingChild.get() ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
						: CompletableFuture.completedFuture(null);
			}
		});

		Throwable failure = assertThrows(java.util.concurrent.CompletionException.class,
				() -> reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join());
		Reward.RewardReplayFailure checkpoint = findCheckpoint(failure);
		assertTrue(checkpoint.getReplayRegistryFingerprints().containsKey("AsyncReward"));
		assertTrue(checkpoint.getReplayRegistryFingerprints().containsKey("AsyncReward/0/child"));

		handler.getInjectedRewards().add(0, new RewardInject("Inserted") {
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				insertedInvocations.incrementAndGet();
				return null;
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class, Map.class, boolean.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		CompletionStage<Void> resumed = (CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0,
				state.newInstance(checkpoint.getReplayProgress(), checkpoint.getReplayRegistryFingerprints(), false),
				"AsyncReward");

		assertThrows(java.util.concurrent.CompletionException.class, () -> resumed.toCompletableFuture().join());
		assertEquals(1, parentInvocations.get());
		assertEquals(0, insertedInvocations.get());
	}

	@Test
	void replayProgressDoesNotSuppressASecondSameNamedChildOccurrence() throws Exception {
		AtomicInteger deliveries = new AtomicInteger();
		handler.getInjectedRewards().add(new RewardInject("Deliver") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				deliveries.incrementAndGet();
				return CompletableFuture.completedFuture(null);
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> constructor = stateType.getDeclaredConstructor(java.util.Map.class);
		constructor.setAccessible(true);
		Object state = constructor.newInstance((Object) null);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class);
		replay.setAccessible(true);
		((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state,
				"root/Rewards/child:0")).toCompletableFuture().join();
		((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state,
				"root/Rewards/child:1")).toCompletableFuture().join();
		assertEquals(2, deliveries.get());
	}

	@Test
	void replaySelectionKeepsTheFirstNondeterministicChoice() {
		HashMap<String, String> placeholders = new HashMap<>();
		AtomicInteger selections = new AtomicInteger();
		assertEquals("first", Reward.replaySelection(placeholders,
				() -> selections.incrementAndGet() == 1 ? "first" : "second"));
		assertEquals("first", Reward.replaySelection(placeholders,
				() -> selections.incrementAndGet() == 1 ? "first" : "second"));
		assertEquals(1, selections.get());
	}

	@Test
	void independentAsyncRewardOccurrencesReceiveDifferentDurableIds() {
		List<String> occurrences = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("Capture") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				occurrences.add(Reward.currentReplayOccurrenceId());
				return CompletableFuture.completedFuture(null);
			}
		});

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();

		assertEquals(2, occurrences.size());
		assertNotEquals(occurrences.get(0), occurrences.get(1));
	}

	@Test
	void retryOfTheSameOccurrenceRetainsItsId() throws Exception {
		List<String> occurrences = new ArrayList<>();
		handler.getInjectedRewards().add(new RewardInject("Capture") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				occurrences.add(Reward.currentReplayOccurrenceId());
				return CompletableFuture.completedFuture(null);
			}
		});
		Class<?> stateType = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
		java.lang.reflect.Constructor<?> state = stateType.getDeclaredConstructor(Map.class);
		state.setAccessible(true);
		java.lang.reflect.Method replay = Reward.class.getDeclaredMethod("giveInjectedRewardsAsync",
				AdvancedCoreUser.class, HashMap.class, int.class, stateType, String.class, String.class);
		replay.setAccessible(true);
		for (int attempt = 0; attempt < 2; attempt++) {
			((CompletionStage<Void>) replay.invoke(reward, user, new HashMap<>(), 0, state.newInstance((Object) null),
					"AsyncReward", "occurrence-1")).toCompletableFuture().join();
		}

		assertEquals(List.of("occurrence-1", "occurrence-1"), occurrences);
	}

	@Test
	void nestedAsyncInjectorCanAwaitTheSameSharedInjectorWithoutDeadlocking() {
		AtomicInteger invocations = new AtomicInteger();
		RewardInject nested = new RewardInject("Nested") {
			@Override public boolean supportsAsyncRequest() { return true; }
			@Override public boolean supportsAsyncSynchronization() { return false; }
			@Override public Object onRewardRequest(Reward ignored, AdvancedCoreUser ignoredUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) { return null; }
			@Override public CompletionStage<Object> onRewardRequestAsync(Reward current, AdvancedCoreUser currentUser,
					ConfigurationSection ignoredData, HashMap<String, String> ignoredPlaceholders) {
				if (invocations.incrementAndGet() == 1) {
					return current.giveInjectedRewardsAsync(currentUser, new HashMap<>()).thenApply(ignored -> null);
				}
				return CompletableFuture.completedFuture(null);
			}
		};
		nested.synchronize();
		handler.getInjectedRewards().add(nested);

		reward.giveInjectedRewardsAsync(user, new HashMap<>()).toCompletableFuture().join();
		assertEquals(2, invocations.get());
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

	private Reward.RewardReplayFailure findCheckpoint(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof Reward.RewardReplayFailure) return (Reward.RewardReplayFailure) current;
		}
		throw new AssertionError("missing replay checkpoint", failure);
	}
}
