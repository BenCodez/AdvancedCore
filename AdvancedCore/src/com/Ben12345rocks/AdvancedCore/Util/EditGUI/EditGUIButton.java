package com.Ben12345rocks.AdvancedCore.Util.EditGUI;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequestBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.Listener;

public abstract class EditGUIButton extends BInventoryButton {

	private String key;

	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/**
	 * @param key
	 *            the key to set
	 */
	public void setKey(String key) {
		this.key = key;
	}

	private Object value;

	@SuppressWarnings("unchecked")
	public EditGUIButton(ItemBuilder item, String key, Object value, EditGUIValueType type) {
		super(item);
		setValueType(type);
		this.key = key;
		this.value = value;
		if (!type.equals(EditGUIValueType.LIST)) {
			getBuilder().setName("&cSet " + type.toString() + " for " + key);
			getBuilder().addLoreLine("&cCurrent value: " + value);
		} else {
			getBuilder().setName("&cEdit list for " + key);
			getBuilder().addLoreLine(ArrayUtils.getInstance().makeStringList((ArrayList<String>) value));
		}
	}

	private EditGUIValueType type;

	public EditGUIButton setValueType(EditGUIValueType type) {
		this.type = type;
		return this;
	}

	private ArrayList<String> options = new ArrayList<String>();

	public EditGUIButton setOptions(String... str) {
		for (String s : str) {
			options.add(s);
		}
		return this;
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (type.equals(EditGUIValueType.BOOLEAN)) {
			new ValueRequestBuilder(new BooleanListener() {

				@Override
				public void onInput(Player player, boolean value) {
					setValue(player, value);
				}
			}).currentValue(value.toString()).request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.NUMBER)) {
			new ValueRequestBuilder(new Listener<Number>() {
				@Override
				public void onInput(Player player, Number number) {
					setValue(player, number.doubleValue());
				}
			}, new Number[] { 0, 10, 25, 50, 100, 500, 1000, (Number) value }).currentValue(value.toString())
					.request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.STRING)) {
			new ValueRequestBuilder(new Listener<String>() {
				@Override
				public void onInput(Player player, String value) {
					setValue(player, value);
				}
			}, ArrayUtils.getInstance().convert(options)).currentValue(value.toString()).allowCustomOption(true)
					.request(clickEvent.getPlayer());
		} else if (type.equals(EditGUIValueType.LIST)) {
			BInventory inv = new BInventory("Edit list: " + key);
			inv.setMeta(clickEvent.getPlayer(), "Value", value);
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.EMERALD_BLOCK).setName("&cAdd value")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					new ValueRequestBuilder(new Listener<String>() {
						@Override
						public void onInput(Player player, String add) {
							@SuppressWarnings("unchecked")
							ArrayList<String> list = (ArrayList<String>) getData("Value");
							list.add(add);
							setValue(player, value);
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
							ArrayList<String> list = (ArrayList<String>) getData("Value");
							list.remove(add);
							setValue(player, value);
						}
					}, ArrayUtils.getInstance().convert((ArrayList<String>) getData("Value")))
							.request(clickEvent.getPlayer());
				}
			});
		}
	}

	/**
	 * @return the type
	 */
	public EditGUIValueType getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(EditGUIValueType type) {
		this.type = type;
	}

	public abstract void setValue(Player player, Object value);

}
