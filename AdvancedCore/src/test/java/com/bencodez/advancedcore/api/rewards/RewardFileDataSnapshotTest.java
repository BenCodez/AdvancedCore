package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.files.FilesManager;

class RewardFileDataSnapshotTest {

    @TempDir
    File tempDir;

    @AfterEach
    void tearDown() {
        AdvancedCorePlugin.setInstance(null);
    }

    @Test
    void snapshotReplacementDropsKeysRemovedFromSourceSection() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCorePlugin.setInstance(plugin);

        File target = new File(tempDir, "Queued.yml");
        YamlConfiguration existing = new YamlConfiguration();
        existing.set("Commands", List.of("old command"));
        existing.set("Messages", List.of("old message"));
        existing.save(target);

        Reward reward = mock(Reward.class);
        when(reward.getFile()).thenReturn(target);
        when(reward.getName()).thenReturn("Queued");
        RewardFileData rewardFileData = new RewardFileData(reward, tempDir);

        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("Messages", List.of("new message"));

        FilesManager filesManager = mock(FilesManager.class);
        try (MockedStatic<FilesManager> files = org.mockito.Mockito.mockStatic(FilesManager.class)) {
            files.when(FilesManager::getInstance).thenReturn(filesManager);
            rewardFileData.setData(replacement);
        }

        ArgumentCaptor<FileConfiguration> saved = ArgumentCaptor.forClass(FileConfiguration.class);
        verify(filesManager).editFile(eq(target), saved.capture());
        assertFalse(saved.getValue().contains("Commands"),
                "reused generated snapshots must not retain actions removed from the source reward");
        assertEquals(List.of("new message"), saved.getValue().getStringList("Messages"));
    }
}
