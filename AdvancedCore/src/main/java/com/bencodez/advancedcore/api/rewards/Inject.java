package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventoryButton;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an injectable reward configuration.
 */
public class Inject {
	/**
	 * @return the list of edit buttons
	 */
	@Getter
	/**
	 * @param editButtons the list of edit buttons to set
	 */
	@Setter
	private ArrayList<BInventoryButton> editButtons = new ArrayList<>();

	/**
	 * @return true if this is an internal reward, false otherwise
	 */
	@Getter
	/**
	 * @param internalReward whether this is an internal reward
	 */
	@Setter
	private boolean internalReward = false;

	/**
	 * @return the configuration path
	 */
	@Getter
	/**
	 * @param path the configuration path to set
	 */
	@Setter
	private String path;

	/**
	 * @return the priority value
	 */
	@Getter
	/**
	 * @param priority the priority value to set
	 */
	@Setter
	private int priority = 50;

	/**
	 * Constructs a new Inject with the specified path.
	 * 
	 * @param path the configuration path
	 */
	public Inject(String path) {
		this.path = path;
	}
}
