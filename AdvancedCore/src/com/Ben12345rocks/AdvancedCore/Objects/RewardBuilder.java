package com.Ben12345rocks.AdvancedCore.Objects;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class RewardBuilder {
	private FileConfiguration data;
	private String prefix = "";
	private String path;
	private HashMap<String, String> placeholders;
	private boolean giveOffline;
	private boolean online;

	public RewardBuilder(FileConfiguration data, String path) {
		this.data = data;
		this.path = path;
		giveOffline = true;
		placeholders = new HashMap<String, String>();
		LocalDateTime ldt = LocalDateTime.now();
		Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		placeholders.put("Date", "" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
	}

	public RewardBuilder checkOffline(boolean giveOffline) {
		this.giveOffline = giveOffline;
		return this;
	}

	public boolean isGiveOffline() {
		return giveOffline;
	}

	public void send(User user) {
		RewardHandler.getInstance().giveReward(user, prefix, data, path, online, giveOffline, placeholders);
	}

	public void send(User... users) {
		for (User user : users) {
			send(user);
		}
	}

	public RewardBuilder setGiveOffline(boolean giveOffline) {
		this.giveOffline = giveOffline;
		return this;
	}

	public RewardBuilder setOnline(boolean online) {
		this.online = online;
		return this;
	}

	public RewardBuilder withPlaceHolder(HashMap<String, String> placeholders) {
		this.placeholders.putAll(placeholders);
		return this;
	}

	public RewardBuilder withPlaceHolder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public RewardBuilder withPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

}
