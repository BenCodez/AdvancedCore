package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract base class for GUI edit values.
 */
public abstract class EditGUIValue {
	/**
	 * Whether the value can be retrieved.
	 * 
	 * @return whether the value can be retrieved
	 * @param canGetValue whether the value can be retrieved
	 */
	@Getter
	@Setter
	private boolean canGetValue = true;

	/**
	 * The current value.
	 * 
	 * @return the current value
	 * @param currentValue the current value
	 */
	@Getter
	@Setter
	private Object currentValue;

	/**
	 * The input method.
	 * 
	 * @return the input method
	 * @param inputMethod the input method
	 */
	@Getter
	@Setter
	private InputMethod inputMethod;

	/**
	 * The inventory.
	 * 
	 * @return the inventory
	 * @param inv the inventory
	 */
	@Getter
	@Setter
	private BInventory inv;

	/**
	 * The key.
	 * 
	 * @return the key
	 * @param key the key
	 */
	@Getter
	@Setter
	private String key;

	/**
	 * The lore lines.
	 * 
	 * @return the lore lines
	 * @param lores the lore lines
	 */
	@Getter
	@Setter
	private ArrayList<String> lores;

	/**
	 * The options.
	 * 
	 * @return the options
	 */
	@Getter
	private ArrayList<String> options = new ArrayList<>();

	/**
	 * Adds lore lines to this value.
	 *
	 * @param lore the lore lines to add
	 * @return this instance
	 */
	public EditGUIValue addLore(ArrayList<String> lore) {
		if (lores == null) {
			lores = new ArrayList<>();
		}
		lores.addAll(lore);
		return this;
	}

	/**
	 * Adds a lore line to this value.
	 *
	 * @param lore the lore line to add
	 * @return this instance
	 */
	public EditGUIValue addLore(String lore) {
		if (lores == null) {
			lores = new ArrayList<>();
		}
		lores.add(lore);
		return this;
	}

	/**
	 * Adds options to this value.
	 *
	 * @param str the options to add
	 * @return this instance
	 */
	public EditGUIValue addOptions(String... str) {
		for (String s : str) {
			options.add(s);
		}
		return this;
	}

	/**
	 * Checks if the reward edit data contains this key.
	 *
	 * @param rewardEditData the reward edit data
	 * @return true if key exists
	 */
	public boolean containsKey(RewardEditData rewardEditData) {
		return rewardEditData.hasPath(getKey());
	}

	/**
	 * Gets the type of this value.
	 *
	 * @return the type
	 */
	public abstract String getType();

	/**
	 * Sets the input method for this value.
	 *
	 * @param inputMethod the input method
	 * @return this instance
	 */
	public EditGUIValue inputMethod(InputMethod inputMethod) {
		this.inputMethod = inputMethod;
		return this;
	}

	/**
	 * Handles click events for this value.
	 *
	 * @param event the click event
	 */
	public abstract void onClick(ClickEvent event);
}
