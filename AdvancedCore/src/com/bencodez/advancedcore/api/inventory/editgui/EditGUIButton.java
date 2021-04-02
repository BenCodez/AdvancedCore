package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
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
	private EditGUIValue editer;

	public EditGUIButton(EditGUIValue editer) {
		super(new ItemBuilder(Material.PAPER));
		this.editer = editer;
	}

	public EditGUIButton(ItemBuilder item, EditGUIValue editer) {
		super(item);
		this.editer = editer;
	}

	@Deprecated
	public EditGUIButton(ItemBuilder itemBuilder, String key, Object value, EditGUIValueType type) {
		super(itemBuilder);
		switch (type) {
		case BOOLEAN:
			editer = new EditGUIValueBoolean(key, value) {

				@Override
				public void setValue(Player player, boolean value) {
					setV(player, value);
				}
			};
			break;
		case DOUBLE:
			editer = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.doubleValue());
				}
			};
			break;
		case INT:
			editer = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.intValue());
				}
			};
			break;
		case LIST:
			editer = new EditGUIValueList(key, value) {

				@Override
				public void setValue(Player player, ArrayList<String> value) {
					setV(player, value);
				}
			};
			break;
		case NUMBER:
			editer = new EditGUIValueNumber(key, value) {

				@Override
				public void setValue(Player player, Number value) {
					setV(player, value.doubleValue());
				}
			};
			break;
		case STRING:
			editer = new EditGUIValueString(key, value) {

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
		getEditer().addOptions(str);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		ItemBuilder builder = getBuilder();
		builder.addPlaceholder(placeholders);
		if (!(getEditer() instanceof EditGUIValueList)) {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cSet " + getEditer().getKey());
			}
			builder.setLore("&cCurrent value: " + getEditer().getCurrentValue());
		} else {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cEdit list for " + getEditer().getKey());
			}
			if (getEditer().getCurrentValue() instanceof ArrayList<?>) {
				builder.setLore(
						ArrayUtils.getInstance().makeStringList((ArrayList<String>) getEditer().getCurrentValue()));
			}
		}
		ArrayList<String> lores = getEditer().getLores();
		if (lores != null) {
			for (String t : lores) {
				builder.addLoreLine("&3" + t);
			}
		}
		return builder.toItemStack(player);
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		getEditer().setInv(getInv());
		getEditer().onClick(clickEvent);
	}

	private void setV(Player player, Object value) {
		setValue(player, value);
	}

	public void setValue(Player player, Object value) {

	}
}
