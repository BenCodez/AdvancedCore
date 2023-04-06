package com.bencodez.advancedcore.api.command;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;
import lombok.Setter;

public abstract class TabCompleteHandle {
	@Getter
	@Setter
	private ArrayList<String> replace = new ArrayList<String>();

	@Getter
	@Setter
	private String toReplace;

	public TabCompleteHandle(String toReplace) {
		this.toReplace = toReplace;
		reload();
	}

	public TabCompleteHandle(String toReplace, ArrayList<String> replace) {
		this.toReplace = toReplace;
		this.replace = replace;
	}

	public abstract void reload();

	public abstract void updateReplacements();

	public TabCompleteHandle updateEveryXMinutes(AdvancedCorePlugin plugin, int x) {
		plugin.getTimer().scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				updateReplacements();
			}
		}, x, x, TimeUnit.SECONDS);
		return this;
	}
}
