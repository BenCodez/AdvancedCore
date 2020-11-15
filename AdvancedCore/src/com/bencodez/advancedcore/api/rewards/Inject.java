package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;

import lombok.Getter;
import lombok.Setter;

public class Inject {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private boolean internalReward = false;

	@Getter
	@Setter
	private int priority = 50;
	@Getter
	@Setter
	private ArrayList<EditGUIButton> editButtons = new ArrayList<EditGUIButton>();

	public Inject(String path) {
		this.path = path;
	}
}
