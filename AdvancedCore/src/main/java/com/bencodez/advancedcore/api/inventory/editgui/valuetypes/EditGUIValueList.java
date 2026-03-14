package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.simpleapi.valuerequest.InputMethod;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

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
			setCurrentValue(new ArrayList<String>());
		}
		BInventory inv = new BInventory("Edit list: " + getKey());
		inv.setMeta(clickEvent.getPlayer(), "Value", getCurrentValue());
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.EMERALD_BLOCK).setName("&cAdd value")) {
			@Override
			public void onClick(ClickEvent clickEvent) {
				new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
						getInputMethod()).requestString(clickEvent.getPlayer(), (String) null, null, true,
						"Type cancel to cancel", (Player player, String add) -> {
							@SuppressWarnings("unchecked")
							ArrayList<String> list = (ArrayList<String>) getMeta(player, "Value");
							if (list == null) {
								list = new ArrayList<String>();
							}
							list.add(add);
							setCurrentValue(list);
							setValue(player, list);
							sendMessage(player, "&cAdded " + add + " to " + getKey());
						});
			}
		});
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cRemove value")) {
			@SuppressWarnings("unchecked")
			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> list = (ArrayList<String>) getMeta(clickEvent.getPlayer(), "Value");
				if (!list.isEmpty()) {
					InputMethod removeMethod = getInputMethod() == InputMethod.BOOK ? InputMethod.CHAT : getInputMethod();
					new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
							removeMethod).requestString(clickEvent.getPlayer(), (String) null, new ArrayList<String>(list), false,
							"Type cancel to cancel", (Player player, String remove) -> {
								ArrayList<String> currentList = (ArrayList<String>) getMeta(player, "Value");
								currentList.remove(remove);
								setCurrentValue(currentList);
								setValue(player, currentList);
								sendMessage(player, "&cRemoved " + remove + " from " + getKey());
							});
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