package com.bencodez.advancedcore.api.rewards;

import java.util.HashMap;
import java.util.Map.Entry;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.array.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

public class RewardOptions {

	@Getter
	private boolean checkRepeat = true;
	private boolean checkTimed = true;

	@Getter
	private boolean forceOffline = false;

	private boolean giveOffline = true;

	private boolean ignoreChance = false;

	@Getter
	private boolean ignoreRequirements = false;

	private boolean online = true;

	@Getter
	@Setter
	private boolean onlineSet = false;

	private HashMap<String, String> placeholders = new HashMap<String, String>();

	private String prefix = "";

	@Getter
	private String server = "";

	private String suffix = "";

	@Getter
	private boolean useDefaultWorlds = true;

	public RewardOptions disableDefaultWorlds() {
		useDefaultWorlds = false;
		return this;
	}

	@Getter
	private long orginalTrigger = -1;

	public RewardOptions() {
	}

	public RewardOptions orginalTrigger(long trigger) {
		orginalTrigger = trigger;
		return this;
	}

	public RewardOptions addPlaceholder(String arg1, String arg2) {
		getPlaceholders().put(arg1, arg2);
		return this;
	}

	public RewardOptions forceOffline() {
		forceOffline = true;
		return this;
	}

	public HashMap<String, String> getPlaceholders() {
		return placeholders;
	}

	/**
	 * @return the prefix
	 */
	public String getPrefix() {
		return prefix;
	}

	/**
	 * @return the suffix
	 */
	public String getSuffix() {
		return suffix;
	}

	public boolean isCheckTimed() {
		return checkTimed;
	}

	public boolean isGiveOffline() {
		return giveOffline;
	}

	public boolean isIgnoreChance() {
		return ignoreChance;
	}

	public boolean isOnline() {
		return online;
	}

	public RewardOptions setCheckRepeat(boolean checkRepeat) {
		this.checkRepeat = checkRepeat;
		return this;
	}

	public RewardOptions setCheckTimed(boolean checkTimed) {
		this.checkTimed = checkTimed;
		return this;
	}

	public RewardOptions setGiveOffline(boolean giveOffline) {
		this.giveOffline = giveOffline;
		return this;
	}

	public RewardOptions setIgnoreChance(boolean ignoreChance) {
		this.ignoreChance = ignoreChance;
		return this;
	}

	public RewardOptions setIgnoreRequirements(boolean ignoreRequirements) {
		this.ignoreRequirements = ignoreRequirements;
		return this;
	}

	public RewardOptions setOnline(boolean online) {
		this.online = online;
		this.onlineSet = true;
		return this;
	}

	public RewardOptions setPlaceholders(HashMap<String, String> placeholders) {
		this.placeholders = placeholders;
		return this;
	}

	public RewardOptions setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public RewardOptions setServer(boolean b) {
		if (b) {
			this.server = AdvancedCorePlugin.getInstance().getOptions().getServer();
			addPlaceholder("Server", this.server);
		}
		return this;
	}

	public RewardOptions setServer(String server) {
		this.server = server;
		addPlaceholder("Server", this.server);
		return this;
	}

	public RewardOptions setSuffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	@Override
	public String toString() {
		String str = "Online: " + online + ", ";
		str += "OnlineSet: " + onlineSet + ", ";
		str += "GiveOffline: " + giveOffline + ", ";
		str += "ForceOffline: " + forceOffline + ", ";
		str += "CheckTimed: " + checkTimed + ", ";
		str += "IgnoreChance: " + ignoreChance + ", ";
		str += "IgnoreRequirements: " + ignoreRequirements + ", ";
		str += "Placeholders: " + ArrayUtils.makeString(placeholders) + ", ";
		str += "Prefix: " + prefix + ", ";
		str += "Suffix: " + suffix;
		return str;

	}

	public RewardOptions withPlaceHolder(HashMap<String, String> placeholders2) {
		for (Entry<String, String> entry : placeholders2.entrySet()) {
			placeholders.put(entry.getKey(), entry.getValue());
		}
		return this;
	}

}
