package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;
import com.bencodez.simpleapi.array.ArrayUtils;

/**
 * Abstract GUI value for list editing.
 */
public abstract class EditGUIValueList extends EditGUIValue {
	/**
	 * Constructor for EditGUIValueList.
	 *
	 * @param key the key
	 * @param value the initial value
	 */
	public EditGUIValueList(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
	}

	@Override
	public String getType() {
		return "list";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (getCurrentValue() == null) {
			setCurrentValue(new ArrayList<>());
		}
		BInventory inv = new BInventory("Edit list: " + getKey());
		inv.setMeta(clickEvent.getPlayer(), "Value", getCurrentValue());
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.EMERALD_BLOCK).setName("&cAdd value")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				new ValueRequestBuilder(new Listener<String>() {
					@Override
					public void onInput(Player player, String add) {
						@SuppressWarnings("unchecked")
						ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
						if (list == null) {
							list = new ArrayList<>();
						}
						list.add(add);
						setValue(player, list);
						sendMessage(player, "&cAdded " + add + " to " + getKey());
					}
				}, new String[] {}).request(clickEvent.getPlayer());
			}
		});
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cRemove value")) {

			@SuppressWarnings("unchecked")
			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> list = (ArrayList<String>) getMeta(clickEvent.getPlayer(), "Value");
				if (!list.isEmpty()) {
					new ValueRequestBuilder(new Listener<String>() {
						@Override
						public void onInput(Player player, String add) {
							ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
							list.remove(add);
							setValue(player, list);
							sendMessage(player, "&cRemoved " + add + " from " + getKey());
						}
					}, ArrayUtils.convert((ArrayList<String>) getMeta(clickEvent.getPlayer(), "Value")))
							.usingMethod(InputMethod.INVENTORY).allowCustomOption(false)
							.request(clickEvent.getPlayer());
				} else {
					clickEvent.getPlayer().sendMessage("No values to remove");
				}
			}
		});
		inv.openInventory(clickEvent.getPlayer());
	}

	/**
	 * Sets the list value.
	 *
	 * @param player the player
	 * @param value the list to set
	 */
	public abstract void setValue(Player player, ArrayList<String> value);
}
