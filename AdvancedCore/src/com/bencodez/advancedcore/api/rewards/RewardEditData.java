package com.bencodez.advancedcore.api.rewards;

public class RewardEditData {
	private Reward reward;
	private DirectlyDefinedReward directlyDefinedReward;
	public RewardEditData(Reward reward) {
		this.reward = reward;
	}
	
	public RewardEditData(DirectlyDefinedReward directlyDefinedReward) {
		this.directlyDefinedReward = directlyDefinedReward;
	}
	
	public void setValue(String path, Object value) {
		if (reward != null) {
			reward.getConfig().set(path, value);
		} else if (directlyDefinedReward != null) { 
			directlyDefinedReward.setValue(path, value);
		}
	}
}
