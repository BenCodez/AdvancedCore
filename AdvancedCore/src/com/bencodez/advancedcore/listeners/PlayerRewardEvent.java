package com.bencodez.advancedcore.listeners;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerRewardEvent.
 */
public class PlayerRewardEvent extends Event {

	/** The Constant handlers. */
	private static final HandlerList handlers = new HandlerList();

	/**
	 * Gets the handler list.
	 *
	 * @return the handler list
	 */
	public static HandlerList getHandlerList() {
		return handlers;
	}

	/** The player. */
	private AdvancedCoreUser player;

	/** The reward. */
	private Reward reward;
	private RewardOptions rewardOptions;

	/** The cancelled. */
	private boolean cancelled;

	/**
	 * Instantiates a new player reward event.
	 *
	 * @param reward        the reward
	 * @param player        the player
	 * @param rewardOptions rewardOptions
	 */
	public PlayerRewardEvent(Reward reward, AdvancedCoreUser player, RewardOptions rewardOptions) {
		super(true);
		setPlayer(player);
		setReward(reward);
		setRewardOptions(rewardOptions);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.bukkit.event.Event#getHandlers()
	 */
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	/**
	 * Gets the player.
	 *
	 * @return the player
	 */
	public AdvancedCoreUser getPlayer() {
		return player;
	}

	/**
	 * Gets the reward.
	 *
	 * @return the reward
	 */
	public Reward getReward() {
		return reward;
	}

	/**
	 * @return the rewardOptions
	 */
	public RewardOptions getRewardOptions() {
		return rewardOptions;
	}

	/**
	 * Checks if is cancelled.
	 *
	 * @return true, if is cancelled
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Sets the cancelled.
	 *
	 * @param bln the new cancelled
	 */
	public void setCancelled(boolean bln) {
		cancelled = bln;
	}

	/**
	 * Sets the player.
	 *
	 * @param player the new player
	 */
	public void setPlayer(AdvancedCoreUser player) {
		this.player = player;
	}

	/**
	 * Sets the reward.
	 *
	 * @param reward the new reward
	 */
	public void setReward(Reward reward) {
		this.reward = reward;
	}

	/**
	 * @param rewardOptions the rewardOptions to set
	 */
	public void setRewardOptions(RewardOptions rewardOptions) {
		this.rewardOptions = rewardOptions;
	}

}
