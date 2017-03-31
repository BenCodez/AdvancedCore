package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.HashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class RewardBuilder {
	private FileConfiguration data;
	private String prefix = "";
	private String path;
	private HashMap<String, String> placeholders;
	private boolean giveOffline;
	private boolean checkTimed;

	public RewardBuilder(FileConfiguration data, String path) {
		this.data = data;
		this.path = path;
		this.placeholders = new HashMap<String, String>();
		this.giveOffline = true;
		this.checkTimed = true;
	}

	public RewardBuilder withPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public RewardBuilder withPlaceHolder(HashMap<String, String> placeholders) {
		this.placeholders = placeholders;
		return this;
	}

	public RewardBuilder withPlaceHolder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public RewardBuilder checkOffline(boolean giveOffline) {
		this.giveOffline = giveOffline;
		return this;
	}

	public RewardBuilder checkTimed(boolean checkTimed) {
		this.checkTimed = checkTimed;
		return this;
	}

	public void send(User user) {
		RewardHandler.getInstance().giveReward(user, prefix, data, path, giveOffline, checkTimed, placeholders);
	}

}
