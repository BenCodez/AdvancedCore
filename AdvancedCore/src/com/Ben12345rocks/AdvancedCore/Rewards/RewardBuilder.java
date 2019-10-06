package com.Ben12345rocks.AdvancedCore.Rewards;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;

public class RewardBuilder {
	private ConfigurationSection data;
	private String path;
	private Reward reward;
	private RewardOptions rewardOptions;

	public RewardBuilder(ConfigurationSection data, String path) {
		this.data = data;
		this.path = path;
		this.rewardOptions = new RewardOptions();
		LocalDateTime ldt = LocalDateTime.now();
		Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		this.rewardOptions.getPlaceholders().put("Date",
				"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
	}

	public RewardBuilder(ConfigurationSection data, String path, RewardOptions rewardOptions) {
		this.data = data;
		this.path = path;
		this.rewardOptions = rewardOptions;
		if (rewardOptions == null) {
			this.rewardOptions = new RewardOptions();
		}
		LocalDateTime ldt = LocalDateTime.now();
		Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		this.rewardOptions.getPlaceholders().put("Date",
				"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
	}

	public RewardBuilder(Reward reward) {
		this.reward = reward;
		this.rewardOptions = new RewardOptions();
		LocalDateTime ldt = LocalDateTime.now();
		Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		this.rewardOptions.getPlaceholders().put("Date",
				"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
	}

	public RewardBuilder(Reward reward, RewardOptions rewardOptions) {
		this.reward = reward;
		this.rewardOptions = rewardOptions;
		if (rewardOptions == null) {
			this.rewardOptions = new RewardOptions();
		}
		LocalDateTime ldt = LocalDateTime.now();
		Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		this.rewardOptions.getPlaceholders().put("Date",
				"" + new SimpleDateFormat("EEE, d MMM yyyy HH:mm").format(date));
	}

	public RewardOptions getRewardOptions() {
		return rewardOptions;
	}

	public void send(ArrayList<User> users) {
		for (User user : users) {
			send(user);
		}
	}

	public RewardBuilder setServer(boolean b) {
		if (b) {
			getRewardOptions().setServer(b);
		}
		return this;
	}

	public void send(OfflinePlayer p) {
		send(UserManager.getInstance().getUser(p));
	}

	public void send(Player p) {
		send(UserManager.getInstance().getUser(p));
	}

	public void send(User user) {
		if (reward == null) {
			if (data != null) {
				RewardHandler.getInstance().giveReward(user, data, path, rewardOptions);
			}
		} else {
			RewardHandler.getInstance().giveReward(user, reward, rewardOptions);
		}
	}

	public void send(User... users) {
		for (User user : users) {
			send(user);
		}
	}

	public RewardBuilder setCheckTimed(boolean checkTimed) {
		getRewardOptions().setCheckTimed(checkTimed);
		return this;
	}

	public RewardBuilder setGiveOffline(boolean giveOffline) {
		getRewardOptions().setGiveOffline(giveOffline);
		return this;
	}

	public RewardBuilder setIgnoreChance(boolean ignoreChance) {
		getRewardOptions().setIgnoreChance(ignoreChance);
		return this;
	}

	public RewardBuilder setIgnoreRequirements(boolean ignoreRequirements) {
		getRewardOptions().setIgnoreRequirements(ignoreRequirements);
		return this;
	}

	public RewardBuilder setOnline(boolean online) {
		getRewardOptions().setOnline(online);
		return this;
	}

	/**
	 * @param reward
	 *            the reward to set
	 */
	public void setReward(Reward reward) {
		this.reward = reward;
	}

	public RewardBuilder setRewardOptions(RewardOptions rewardOptions) {
		this.rewardOptions = rewardOptions;
		return this;
	}

	public RewardBuilder withPlaceHolder(HashMap<String, String> placeholders) {
		this.rewardOptions.getPlaceholders().putAll(placeholders);
		return this;
	}

	public RewardBuilder withPlaceHolder(String toReplace, String replaceWith) {
		this.rewardOptions.getPlaceholders().put(toReplace, replaceWith);
		return this;
	}

	public RewardBuilder withPrefix(String prefix) {
		this.rewardOptions.setPrefix(prefix);
		return this;
	}

	public RewardBuilder withSuffix(String suffix) {
		this.rewardOptions.setSuffix(suffix);
		return this;
	}

}
