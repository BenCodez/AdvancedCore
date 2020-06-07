package com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes;

import java.util.ArrayList;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequestBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.Listener;

public abstract class EditGUIValueList extends EditGUIValue {
	public EditGUIValueList(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (getCurrentValue() == null) {
			setCurrentValue(new ArrayList<String>());
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
							list = new ArrayList<String>();
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
				new ValueRequestBuilder(new Listener<String>() {
					@Override
					public void onInput(Player player, String add) {
						ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
						list.remove(add);
						setValue(player, list);
						sendMessage(player, "&cRemoved " + add + " from " + getKey());
					}
				}, ArrayUtils.getInstance().convert((ArrayList<String>) getData("Value")))
						.request(clickEvent.getPlayer());
			}
		});
		inv.openInventory(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, ArrayList<String> value);
}
