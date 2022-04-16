package com.bencodez.advancedcore.api.rewards;

import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public class RepeatHandle {
	@Getter
	@Setter
	private int amount = -1;

	@Getter
	@Setter
	private boolean autoStop = false;
	@Getter
	@Setter
	private boolean enabled = false;
	private int globalAmount = 0;
	@Getter
	@Setter
	private boolean repeatOnStartup = false;
	@Getter
	private Reward reward;
	@Getter
	@Setter
	private long timeBetween = 0;

	public RepeatHandle(Reward reward) {
		this.reward = reward;
		ConfigurationSection data = reward.getConfig().getConfigData().getConfigurationSection("Repeat");
		if (data != null) {
			enabled = data.getBoolean("Enabled", false);
			timeBetween = data.getLong("TimeBetween", 0);
			amount = data.getInt("Amount", -1);
			repeatOnStartup = data.getBoolean("RepeatOnStartup", false);
			autoStop = data.getBoolean("AutoStop", true);
		}
	}

	public void giveRepeat(AdvancedCorePlugin plugin, AdvancedCoreUser user) {
		if (repeatOnStartup) {
			return;
		}
		plugin.debug("Giving repeat reward in " + timeBetween);
		plugin.getRewardHandler().getRepeatTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				if (amount > 0) {
					int cAmount = user.getRepeatAmount(reward);
					if (cAmount >= amount) {
						user.setRepeatAmount(reward, 0);
						cancel();
						return;
					}
					// add amount
					user.setRepeatAmount(reward, cAmount + 1);
					if (autoStop) {
						if (!reward.canGiveReward(user, new RewardOptions())) {
							user.setRepeatAmount(reward, 0);
							cancel();
							return;
						} else {
							giveReward(plugin, user, true);
							cancel();
							return;
						}
					} else {
						giveReward(plugin, user, false);
						cancel();
						return;
					}

				} else {
					if (autoStop) {
						if (!reward.canGiveReward(user, new RewardOptions())) {
							cancel();
							return;
						} else {
							giveReward(plugin, user, true);
							cancel();
							return;
						}
					} else {
						giveReward(plugin, user, false);
						cancel();
						return;
					}
				}
			}
		}, timeBetween);
	}

	public void giveRepeatAll(AdvancedCorePlugin plugin) {
		plugin.getRewardHandler().getRepeatTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				if (amount > 0) {
					if (globalAmount >= amount) {
						cancel();
						return;
					}
					globalAmount++;
				}
				for (Player p : Bukkit.getOnlinePlayers()) {
					AdvancedCoreUser user = AdvancedCorePlugin.getInstance().getUserManager().getUser(p);
					giveReward(plugin, user, false);
					cancel();
					return;
				}
			}
		}, timeBetween);

	}

	public void giveReward(AdvancedCorePlugin plugin, AdvancedCoreUser user, boolean bypassRequirement) {
		AdvancedCorePlugin.getInstance()
				.debug("Giving repeat reward " + reward.getName() + " for " + user.getPlayerName());
		if (bypassRequirement) {
			reward.giveReward(user, new RewardOptions().setIgnoreRequirements(false).setIgnoreChance(false)
					.setCheckRepeat(false).forceOffline());
		} else {
			reward.giveReward(user, new RewardOptions().setCheckRepeat(false).forceOffline());
		}
		giveRepeat(plugin, user);
	}

}
