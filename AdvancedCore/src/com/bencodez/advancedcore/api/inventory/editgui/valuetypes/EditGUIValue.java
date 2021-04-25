package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;

import lombok.Getter;
import lombok.Setter;

public abstract class EditGUIValue {
	@Getter
	@Setter
	private String key;

	@Getter
	@Setter
	private Object currentValue;

	@Getter
	private ArrayList<String> options = new ArrayList<String>();

	@Getter
	@Setter
	private BInventory inv;

	@Getter
	@Setter
	private ArrayList<String> lores;

	@Getter
	@Setter
	private boolean canGetValue = true;

	@Getter
	@Setter
	private InputMethod inputMethod;

	public EditGUIValue inputMethod(InputMethod inputMethod) {
		this.inputMethod = inputMethod;
		return this;
	}

	public boolean containsKey(RewardEditData rewardEditData) {
		return rewardEditData.hasPath(getKey());
	}

	public EditGUIValue addOptions(String... str) {
		for (String s : str) {
			options.add(s);
		}
		return this;
	}

	public abstract void onClick(ClickEvent event);

	public EditGUIValue addLore(String lore) {
		if (lores == null) {
			lores = new ArrayList<String>();
		}
		lores.add(lore);
		return this;
	}
}
