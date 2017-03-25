package com.Ben12345rocks.AdvancedCore.Objects;

import org.bukkit.configuration.file.FileConfiguration;

public class RewardBuilder {
	private FileConfiguration data;
	private String prefix = "";
	private String path;

	public RewardBuilder(FileConfiguration data, String path) {
		this.data = data;
		this.path = path;
	}

	public RewardBuilder withPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public void send(User user) {
		RewardHandler.getInstance().giveReward(user, prefix, data, path);
	}

}
