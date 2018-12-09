package com.Ben12345rocks.AdvancedCore.Util.EditGUI;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequestBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.Listener;

import lombok.Getter;
import lombok.Setter;

public abstract class EditGUIButton extends BInventoryButton {

	@Getter
	@Setter
	private String key;

	@Getter
	@Setter
	private Object currentValue;

	@Getter
	@Setter
	private EditGUIValueType type;

	@Getter
	private ArrayList<String> options = new ArrayList<String>();

	public EditGUIButton(ItemBuilder item, String key, Object value, EditGUIValueType type) {
		super(item);
		setValueType(type);
		this.key = key;
		this.currentValue = value;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ItemStack getItem(Player player) {
		ItemBuilder builder = getBuilder();
		if (!type.equals(EditGUIValueType.LIST)) {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cSet " + type.toString() + " for " + key);
			}
			builder.setLore("&cCurrent value: " + getCurrentValue());
		} else {
			if (!builder.hasCustomDisplayName()) {
				builder.setName("&cEdit list for " + key);
			}
			builder.setLore(ArrayUtils.getInstance().makeStringList((ArrayList<String>) getCurrentValue()));
		}
		return builder.toItemStack(player);
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (type.equals(EditGUIValueType.BOOLEAN)) {
			if (getCurrentValue() == null) {
				setCurrentValue("false");
			}
			new ValueRequestBuilder(new BooleanListener() {

				@Override
				public void onInput(Player player, boolean value) {
					setValue(player, value);
					sendMessage(player, "&cSetting " + getKey() + " to " + value);
				}
			}).currentValue(currentValue.toString()).request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.NUMBER) || type.equals(EditGUIValueType.DOUBLE)) {
			if (getCurrentValue() == null) {
				setCurrentValue(0);
			}
			new ValueRequestBuilder(new Listener<Number>() {

				@Override
				public void onInput(Player player, Number number) {
					setValue(player, number.doubleValue());
					sendMessage(player, "&cSetting " + getKey() + " to " + currentValue);
				}
			}, new Number[] { 0, 10, 25, 50, 100, 500, 1000, (Number) currentValue })
					.currentValue(currentValue.toString()).request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.INT)) {
			if (getCurrentValue() == null) {
				setCurrentValue(0);
			}
			new ValueRequestBuilder(new Listener<Number>() {

				@Override
				public void onInput(Player player, Number number) {
					setValue(player, number.intValue());
					sendMessage(player, "&cSetting " + getKey() + " to " + currentValue);
				}
			}, new Number[] { 0, 10, 25, 50, 100, 500, 1000, (Number) currentValue })
					.currentValue(currentValue.toString()).request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.STRING)) {
			if (getCurrentValue() == null) {
				setCurrentValue("");
			}
			new ValueRequestBuilder(new Listener<String>() {
				@Override
				public void onInput(Player player, String value) {
					setValue(player, value);
					sendMessage(player, "&cSetting " + getKey() + " to " + value);
				}
			}, ArrayUtils.getInstance().convert(options)).currentValue(currentValue.toString()).allowCustomOption(true)
					.request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.LIST)) {
			if (getCurrentValue() == null) {
				setCurrentValue(new ArrayList<String>());
			}
			BInventory inv = new BInventory("Edit list: " + key);
			inv.setMeta(clickEvent.getPlayer(), "Value", currentValue);
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.EMERALD_BLOCK).setName("&cAdd value")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					new ValueRequestBuilder(new Listener<String>() {
						@Override
						public void onInput(Player player, String add) {
							@SuppressWarnings("unchecked")
							ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
							if (list == null) {
								list = new ArrayList<String>();
							}
							list.add(add);
							setValue(player, currentValue);
							sendMessage(player, "&cAdded " + add + " to " + getKey());
						}
					}, new String[] {}).request(clickEvent.getPlayer());
				}
			});
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cRemove value")) {

				@SuppressWarnings("unchecked")
				@Override
				public void onClick(ClickEvent clickEvent) {
					new ValueRequestBuilder(new Listener<String>() {
						@Override
						public void onInput(Player player, String add) {
							ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
							list.remove(add);
							setValue(player, currentValue);
							sendMessage(player, "&cRemoved " + add + " from " + getKey());
						}
					}, ArrayUtils.getInstance().convert((ArrayList<String>) getData("Value")))
							.request(clickEvent.getPlayer());
				}
			});
			inv.openInventory(clickEvent.getPlayer());
		}
	}

	public EditGUIButton setOptions(String... str) {
		for (String s : str) {
			options.add(s);
		}
		return this;
	}

	public abstract void setValue(Player player, Object value);

	public EditGUIButton setValueType(EditGUIValueType type) {
		this.type = type;
		return this;
	}

	@Override
	public EditGUIButton clone() {
		return new EditGUIButton(getBuilder(), getKey(), getCurrentValue(), getType()) {

			@Override
			public void setValue(Player player, Object value) {
				setValue(player, value);
			}
		};
	}
}
