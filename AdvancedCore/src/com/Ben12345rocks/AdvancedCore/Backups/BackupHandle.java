package com.Ben12345rocks.AdvancedCore.Backups;

import java.io.File;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Listeners.DateChangedEvent;
import com.Ben12345rocks.AdvancedCore.Report.Report;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;

public class BackupHandle implements Listener {
	private static BackupHandle instance = new BackupHandle();

	public static BackupHandle getInstance() {
		return instance;
	}

	public BackupHandle() {
	}

	@EventHandler
	public void onDateChange(DateChangedEvent e) {
		if (e.isFake()) {
			// implement later
		}

		Report.getInstance().create(AdvancedCoreHook.getInstance().getPlugin().getDataFolder(), new File(
				AdvancedCoreHook.getInstance().getPlugin().getDataFolder(),
				"Backups" + File.separator + "Backup-" + TimeChecker.getInstance().getTime().getDayOfMonth()));
	}
}
