package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValue;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Button for editing GUI values.
 */
public class EditGUIButton extends BInventoryButton {
	@Getter
	@Setter
	private EditGUIValue editor;

	public EditGUIButton(EditGUIValue editor) {
		super(new ItemBuilder(Material.PAPER));
		this.editor = editor;
	}

	public EditGUIButton(ItemBuilder item, EditGUIValue editor) {
		super(item);
		this.editor = editor;
	}

	public EditGUIButton addLore(String lore) {
		getEditor().addLore(lore);
		return this;
	}

	public EditGUIButton addOptions(String... values) {
		getEditor().addOptions(values);
		return this;
	}

	@Override
	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		ItemBuilder builder = getBuilder();
		builder.addPlaceholder(placeholders);

		if (getEditor() instanceof EditGUIValueInventory) {
			applyInventoryDisplay(builder);
		} else {
			if (!builder.hasCustomDisplayName()) {
				builder.setName(getEditor().getButtonName());
			}
			List<String> lore = getEditor().getButtonLore();
			builder.setLore(lore == null ? new String[0] : lore.toArray(new String[0]));
		}

		ArrayList<String> customLore = getEditor().getLores();
		if (customLore != null) {
			for (String line : customLore) {
				builder.addLoreLine("&3" + line);
			}
		}
		return builder.toItemStack(player);
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		getEditor().setInv(getInv());
		getEditor().onClick(clickEvent);
	}

	public EditGUIButton setName(String name) {
		getBuilder().setName(name);
		return this;
	}

	private void applyInventoryDisplay(ItemBuilder builder) {
		if (!builder.hasCustomDisplayName()) {
			builder.setName("&cSet " + getEditor().getKey());
		}
		builder.setLore("&cClick to open");
	}
}
