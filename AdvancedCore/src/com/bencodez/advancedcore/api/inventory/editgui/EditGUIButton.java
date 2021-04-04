package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValue;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
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

	@Deprecated
	public EditGUIButton(ItemBuilder itemBuilder, String key, Object value, EditGUIValueType type) {
		super(itemBuilder);
		switch (type) {
		case BOOLEAN:
			editor = new EditGUIValueBoolean(key, value) {

				@Override
				public void setValue(Player player, boolean value) {
					setV(player, value);
				}
			};
			break;
		case DOUBLE:
			editor = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.doubleValue());
				}
			};
			break;
		case INT:
			editor = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.intValue());
				}
			};
			break;
		case LIST:
			editor = new EditGUIValueList(key, value) {

				@Override
				public void setValue(Player player, ArrayList<String> value) {
					setV(player, value);
				}
			};
			break;
		case NUMBER:
			editor = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.doubleValue());
				}
			};
			break;
		case STRING:
			editor = new EditGUIValueString(key, value) {

				@Override
				public void setValue(Player player, String value) {
					setV(player, value);
				}
			};
			break;
		default:
			break;

		}
	}

	public EditGUIButton addOptions(String... str) {
		getEditor().addOptions(str);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		ItemBuilder builder = getBuilder();
		builder.addPlaceholder(placeholders);
		if (!(getEditor() instanceof EditGUIValueList)) {
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

	private void setV(Player player, Object value) {
		setValue(player, value);
	}

	public void setValue(Player player, Object value) {

	}
}
