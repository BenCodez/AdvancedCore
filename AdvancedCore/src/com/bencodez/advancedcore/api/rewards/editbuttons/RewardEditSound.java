package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.simpleapi.array.ArrayUtils;

public abstract class RewardEditSound extends RewardEdit {
	public RewardEditSound() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Sound: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Sound.Enabled", reward));

		ArrayList<String> sounds = new ArrayList<String>();
		for (Sound s : Sound.values()) {
			sounds.add(s.toString());
		}

		inv.addButton(getStringButton("Sound.Sound", reward, ArrayUtils.convert(sounds)));
		inv.addButton(getDoubleButton("Sound.Volume", reward));
		inv.addButton(getDoubleButton("Sound.Pitch", reward));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
