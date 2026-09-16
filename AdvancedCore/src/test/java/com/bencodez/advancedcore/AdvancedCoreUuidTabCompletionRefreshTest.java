package com.bencodez.advancedcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.simpleapi.command.TabCompleteHandle;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class AdvancedCoreUuidTabCompletionRefreshTest {
	@Test
	void storageEnumerationIsDeferredAndTheLatestRefreshWins() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		TabCompleteHandle handle = mock(TabCompleteHandle.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		when(handle.getToReplace()).thenReturn("(uuid)");
		when(users.getAllUUIDs()).thenReturn(new ArrayList<>(List.of("old")), new ArrayList<>(List.of("new")));

		ArrayList<Runnable> workers = new ArrayList<>();
		ArrayList<Runnable> mainTasks = new ArrayList<>();
		doAnswer(call -> {
			workers.add(call.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTaskAsynchronously(any(), any());
		doAnswer(call -> {
			mainTasks.add(call.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(any(), any());

		AdvancedCorePlugin.UuidTabCompletionRefresh refresh = new AdvancedCorePlugin.UuidTabCompletionRefresh();
		try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

			refresh.request(plugin, handle);
			verify(users, never()).getAllUUIDs();
			assertEquals(1, workers.size(), "request must enqueue, not enumerate on the caller thread");

			refresh.request(plugin, handle);
			assertEquals(1, workers.size(), "overlapping requests must be coalesced");

			workers.remove(0).run();
			verify(users).getAllUUIDs();
			assertEquals(1, mainTasks.size(), "worker result must be applied on the global scheduler");
			verify(handle, never()).setReplace(any());

			mainTasks.remove(0).run();
			verify(handle, never()).setReplace(any());
			assertEquals(1, workers.size(), "the stale completion must schedule the newest generation");

			workers.remove(0).run();
			assertEquals(1, mainTasks.size());
			mainTasks.remove(0).run();

			ArgumentCaptor<ArrayList<String>> replacement = ArgumentCaptor.forClass(ArrayList.class);
			verify(handle).setReplace(replacement.capture());
			assertEquals(List.of("new"), replacement.getValue());
		}
	}
}
