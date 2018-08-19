package com.Ben12345rocks.AdvancedCore.Rewards;

import java.util.HashMap;

public class RewardOptions {

	public RewardOptions() {
	}

	private boolean online = true;

	public boolean isOnline() {
		return online;
	}

	public RewardOptions setOnline(boolean online) {
		this.online = online;
		return this;
	}

	private boolean giveOffline = true;
	private boolean checkTimed = true;
	private boolean ignoreChance = false;
	private HashMap<String, String> placeholders = new HashMap<String, String>();

	public boolean isGiveOffline() {
		return giveOffline;
	}

	public RewardOptions setGiveOffline(boolean giveOffline) {
		this.giveOffline = giveOffline;
		return this;
	}

	public boolean isCheckTimed() {
		return checkTimed;
	}

	public RewardOptions setCheckTimed(boolean checkTimed) {
		this.checkTimed = checkTimed;
		return this;
	}

	public boolean isIgnoreChance() {
		return ignoreChance;
	}

	public RewardOptions setIgnoreChance(boolean ignoreChance) {
		this.ignoreChance = ignoreChance;
		return this;
	}

	public HashMap<String, String> getPlaceholders() {
		return placeholders;
	}

	public RewardOptions setPlaceholders(HashMap<String, String> placeholders) {
		this.placeholders = placeholders;
		return this;
	}

}
