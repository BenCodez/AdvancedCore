package com.bencodez.advancedcore.api.rewards;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.array.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

public class RewardOptions {

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

	private HashMap<String, String> placeholders = new HashMap<>();

	private String prefix = "";

	@Getter
	private String server = "";

	private String suffix = "";

	@Getter
	private boolean useDefaultWorlds = true;

	@Getter
	private long orginalTrigger = -1;

	/**
	 * Number of completed injection stages recorded for a persisted replay. This
	 * is intentionally internal queue metadata: normal reward dispatch always
	 * starts at zero.
	 */
	@Getter
	@Setter
	private int completedAsyncInjections;

	@Getter
	@Setter
	private Map<String, Integer> asyncReplayProgress = new HashMap<>();

	@Getter
	@Setter
	private Map<String, String> asyncReplayRegistryFingerprints = new HashMap<>();

	/** True when this queued entry used the old count-only replay format. */
	@Getter
	@Setter
	private boolean legacyAsyncReplayCheckpoint;

	@Getter
	@Setter
	private Reward.ReplayState asyncReplayState;

	/**
	 * Stable execution path for a nested asynchronous reward. The path is queue
	 * metadata only; callers that do not participate in replay leave it unset.
	 */
	@Getter
	@Setter
	private String asyncReplayKey;

	/**
	 * Stable identity for one logical queued reward occurrence. It is distinct
	 * from {@link #asyncReplayKey}, which identifies a stage path shared by
	 * independent executions of the same reward definition.
	 */
	@Getter
	@Setter
	private String asyncReplayOccurrenceId;

	@Getter
	@Setter
	private Consumer<Reward.ReplayCheckpoint> asyncReplayCheckpointConsumer;

	public RewardOptions() {
	}

	public RewardOptions addPlaceholder(String arg1, String arg2) {
		getPlaceholders().put(arg1, arg2);
		return this;
	}

	public RewardOptions disableDefaultWorlds() {
		useDefaultWorlds = false;
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

	public RewardOptions orginalTrigger(long trigger) {
		orginalTrigger = trigger;
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

	/**
	 * Makes an isolated option object for one member of an asynchronously
	 * dispatched list. Mutable placeholders and replay metadata must not leak
	 * from one list member into the next.
	 */
	RewardOptions copyForNestedDispatch(String replayKey) {
		RewardOptions copy = new RewardOptions().setCheckTimed(checkTimed).setGiveOffline(giveOffline)
				.setIgnoreChance(ignoreChance).setIgnoreRequirements(ignoreRequirements).setPrefix(prefix).setSuffix(suffix)
				.orginalTrigger(orginalTrigger).setPlaceholders(new HashMap<>(placeholders));
		if (forceOffline) copy.forceOffline();
		if (!useDefaultWorlds) copy.disableDefaultWorlds();
		if (onlineSet) copy.setOnline(online);
		if (!server.isEmpty()) copy.setServer(server);
		copy.setAsyncReplayState(asyncReplayState);
		copy.setAsyncReplayRegistryFingerprints(new HashMap<>(asyncReplayRegistryFingerprints));
		copy.setLegacyAsyncReplayCheckpoint(legacyAsyncReplayCheckpoint);
		copy.setAsyncReplayKey(replayKey);
		copy.setAsyncReplayOccurrenceId(asyncReplayOccurrenceId);
		copy.setAsyncReplayCheckpointConsumer(asyncReplayCheckpointConsumer);
		return copy;
	}

}
