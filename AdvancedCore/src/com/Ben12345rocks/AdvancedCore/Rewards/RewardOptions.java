package com.Ben12345rocks.AdvancedCore.Rewards;

import java.util.HashMap;
import java.util.Map.Entry;

import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

public class RewardOptions {

	private boolean online = true;
	@Getter
	@Setter
	private boolean onlineSet = false;

	@Getter
	private boolean checkRepeat = true;

	private boolean giveOffline = true;

	private boolean checkTimed = true;

	private boolean ignoreChance = false;

	@Getter
	private boolean forceOffline = false;

	@Getter
	private boolean ignoreRequirements = false;

	private HashMap<String, String> placeholders = new HashMap<String, String>();

	private String prefix = "";

	private String suffix = "";

	public RewardOptions() {
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

	public RewardOptions setCheckTimed(boolean checkTimed) {
		this.checkTimed = checkTimed;
		return this;
	}

	public RewardOptions setCheckRepeat(boolean checkRepeat) {
		this.checkRepeat = checkRepeat;
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

	public RewardOptions setSuffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	@Override
	public String toString() {
		String str = "Online: " + online + ", ";
		str += "OnlineSet: " + onlineSet + ", ";
		str += "GiveOffline: " + giveOffline + ", ";
		str += "CheckTimed" + checkTimed + ", ";
		str += "IgnoreChance" + ignoreChance + ", ";
		str += "IgnoreRequirements" + ignoreRequirements + ", ";
		str += "Placeholders" + ArrayUtils.getInstance().makeString(placeholders) + ", ";
		str += "Prefix" + prefix + ", ";
		str += "Suffix" + suffix;
		return str;

	}

	public RewardOptions withPlaceHolder(HashMap<String, String> placeholders2) {
		for (Entry<String, String> entry : placeholders2.entrySet()) {
			placeholders.put(entry.getKey(), entry.getValue());
		}
		return this;
	}

}
