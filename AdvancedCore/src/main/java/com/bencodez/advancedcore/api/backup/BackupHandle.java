package com.bencodez.advancedcore.api.backup;

import java.io.File;
import java.time.LocalDateTime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.time.events.DateChangedEvent;

public class BackupHandle implements Listener {
	private static BackupHandle instance = new BackupHandle();

	public static BackupHandle getInstance() {
		return instance;
	}

	private BackupHandle() {
	}

	public void checkOldBackups() {
		for (File file : new File(AdvancedCorePlugin.getInstance().getDataFolder(), "Backups").listFiles()) {
			long lastModified = file.lastModified();
			if (LocalDateTime.now().minusDays(5).isAfter(MiscUtils.getInstance().getTime(lastModified))) {
				file.delete();
				AdvancedCorePlugin.getInstance().debug("Deleting old backup: " + file.getName());
			}
		}
	}

	@EventHandler
	public void onPostDateChange(DateChangedEvent e) {
		if (!e.getTimeType().equals(TimeType.DAY) || !AdvancedCorePlugin.getInstance().getOptions().isCreateBackups()) {
			return;
		}

		LocalDateTime now = AdvancedCorePlugin.getInstance().getTimeChecker().getTime();
		ZipCreator.getInstance().create(AdvancedCorePlugin.getInstance().getDataFolder(),
				new File(AdvancedCorePlugin.getInstance().getDataFolder(), "Backups" + File.separator + "Backup-"
						+ now.getYear() + "_" + now.getMonth() + "_" + now.getDayOfMonth() + ".zip"));

		checkOldBackups();
	}
}
