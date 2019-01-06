package com.Ben12345rocks.AdvancedCore.Util.EditGUI;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValue;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueBoolean;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueList;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueNumber;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueString;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

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
	public ItemStack getItem(Player player) {
		ItemBuilder builder = getBuilder();
		if (!(getEditer() instanceof EditGUIValueList)) {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cSet " + getEditer().getKey());
			}
			builder.setLore("&cCurrent value: " + getEditer().getCurrentValue());
		} else {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cEdit list for " + getEditer().getKey());
			}
			builder.setLore(ArrayUtils.getInstance().makeStringList((ArrayList<String>) getEditer().getCurrentValue()));
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
