package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;

import lombok.Getter;

public abstract class EditGUIValueConfigurationSection extends EditGUIValue {

	private EditGUI inv;

	@Getter
	private ConfigurationSection section;

	public EditGUIValueConfigurationSection(String key, ConfigurationSection section) {
		setKey(key);
		this.section = section;
	}

	public EditGUIValueConfigurationSection setInv(EditGUI inv) {
		this.inv = inv;
		return this;
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		inv.openInventory(clickEvent.getPlayer());
	}
}
