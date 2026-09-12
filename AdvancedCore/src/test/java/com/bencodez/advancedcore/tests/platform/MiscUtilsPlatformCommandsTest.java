package com.bencodez.advancedcore.tests.platform;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class MiscUtilsPlatformCommandsTest {
    @Test
    void existingListFacadeUsesSharedDispatcherWithoutChangingRenderingOrTiming() throws Exception {
        MiscUtils facade = MiscUtils.getInstance();
        Field field = MiscUtils.class.getDeclaredField("plugin");
        field.setAccessible(true);
        Object previous = field.get(facade);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            field.set(facade, plugin);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("who", "Ben");
            facade.executeConsoleCommands(new ArrayList<>(List.of("/say %who%", "//literal", " third")), placeholders, true);
            verifyNoInteractions(server);
            ArgumentCaptor<Runnable> first = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> second = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> third = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), first.capture());
            verify(scheduler).runTaskLater(eq(plugin), second.capture(), eq(1L));
            verify(scheduler).runTaskLater(eq(plugin), third.capture(), eq(2L));
            first.getValue().run(); second.getValue().run(); third.getValue().run();
            org.mockito.InOrder ordered = inOrder(server);
            ordered.verify(server).dispatchCommand(console, "say Ben");
            ordered.verify(server).dispatchCommand(console, "/literal");
            ordered.verify(server).dispatchCommand(console, " third");
            verifyNoMoreInteractions(server);
        } finally {
            field.set(facade, previous);
        }
    }
}
