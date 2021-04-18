package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValue;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;

import lombok.Getter;
import lombok.Setter;

public class EditGUIButton extends BInventoryButton {

	@Getter
	@Setter
	private EditGUIValue editor;

	public EditGUIButton(EditGUIValue editer) {
		super(new ItemBuilder(Material.PAPER));
		this.editor = editer;
	}

	public EditGUIButton(ItemBuilder item, EditGUIValue editer) {
		super(item);
		this.editor = editer;
	}

	public EditGUIButton addOptions(String... str) {
		getEditor().addOptions(str);
		return this;
	}

	public EditGUIButton setName(String name) {
		this.getBuilder().setName(name);
		return this;
	}

	public EditGUIButton addLore(String lore) {
		getEditor().addLore(lore);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		ItemBuilder builder = getBuilder();
		builder.addPlaceholder(placeholders);
		if ((getEditor() instanceof EditGUIValueInventory)) {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cSet " + getEditor().getKey());
			}
			builder.setLore("&cClick to open");
		} else if (!(getEditor() instanceof EditGUIValueList)) {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cSet " + getEditor().getKey());
			}
			builder.setLore("&cCurrent value: " + getEditor().getCurrentValue());
		} else {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cEdit list for " + getEditor().getKey());
			}
			if (getEditor().getCurrentValue() instanceof ArrayList<?>) {
				builder.setLore(
						ArrayUtils.getInstance().makeStringList((ArrayList<String>) getEditor().getCurrentValue()));
			} else {
				builder.setLore("&cCurrent value: null");
			}
		}
		ArrayList<String> lores = getEditor().getLores();
		if (lores != null) {
			for (String t : lores) {
				builder.addLoreLine("&3" + t);
			}
		}
		return builder.toItemStack(player);
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		getEditor().setInv(getInv());
		getEditor().onClick(clickEvent);
	}
}
