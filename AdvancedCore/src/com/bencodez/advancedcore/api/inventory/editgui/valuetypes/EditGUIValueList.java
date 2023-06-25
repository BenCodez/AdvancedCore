package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;

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
					}, ArrayUtils.getInstance().convert((ArrayList<String>) getMeta(clickEvent.getPlayer(), "Value")))
							.usingMethod(InputMethod.INVENTORY).allowCustomOption(false)
							.request(clickEvent.getPlayer());
				} else {
					clickEvent.getPlayer().sendMessage("No values to remove");
				}
			}
		});
		inv.openInventory(clickEvent.getPlayer());
	}
	
	@Override
	public String getType() {
		return "list";
	}

	public abstract void setValue(Player player, ArrayList<String> value);
}
