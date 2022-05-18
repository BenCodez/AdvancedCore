package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventoryButton;

import lombok.Getter;
import lombok.Setter;

public class Inject {
	@Getter
	@Setter
	private ArrayList<BInventoryButton> editButtons = new ArrayList<BInventoryButton>();

	@Getter
	@Setter
	private boolean internalReward = false;

	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private int priority = 50;

	public Inject(String path) {
		this.path = path;
	}
}
