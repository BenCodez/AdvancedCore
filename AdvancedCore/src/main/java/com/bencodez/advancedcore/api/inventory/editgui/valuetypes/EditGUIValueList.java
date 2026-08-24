package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.simpleapi.valuerequest.InputMethod;

/**
 * Abstract GUI value for list editing.
 */
public abstract class EditGUIValueList extends EditGUIValue {
	public EditGUIValueList(String key, Object value) {
		super(key, value);
	}

	@Override
	public String getType() {
		return "list";
	}

	@Override
	public String getButtonName() {
		return "&cEdit list for " + getKey();
	}

	@Override
	public List<String> getButtonLore() {
		ArrayList<String> current = currentStringList();
		return current.isEmpty() && getCurrentValue() == null ? List.of("&cCurrent value: null") : current;
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		ArrayList<String> initial = currentStringList();
		setCurrentValue(initial);

		BInventory inventory = new BInventory("Edit list: " + getKey());
		inventory.setMeta(clickEvent.getPlayer(), "Value", new ArrayList<>(initial));
		inventory.addButton(createAddButton());
		inventory.addButton(createRemoveButton());
		inventory.openInventory(clickEvent.getPlayer());
	}

	private BInventoryButton createAddButton() {
		return new BInventoryButton(new ItemBuilder(Material.EMERALD_BLOCK).setName("&cAdd value")) {
			@Override
			public void onClick(ClickEvent clickEvent) {
				createValueRequest().requestString(clickEvent.getPlayer(), (String) null, null, true,
						"Type cancel to cancel", (Player player, String add) -> {
							ArrayList<String> list = EditGUIValueList.this.toStringList(getMeta(player, "Value"));
							list.add(add);
							setMeta(player, "Value", list);
							applyValue(player, list, newValue -> setValue(player, new ArrayList<>(newValue)));
							sendMessage(player, "&cAdded " + add + " to " + getKey());
						});
			}
		};
	}

	private BInventoryButton createRemoveButton() {
		return new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cRemove value")) {
			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> list = EditGUIValueList.this.toStringList(getMeta(clickEvent.getPlayer(), "Value"));
				if (list.isEmpty()) {
					clickEvent.getPlayer().sendMessage("No values to remove");
					return;
				}

				InputMethod removeMethod = getInputMethod() == InputMethod.BOOK ? InputMethod.CHAT : getInputMethod();
				createValueRequest(removeMethod).requestString(clickEvent.getPlayer(), (String) null, new ArrayList<>(list),
						false, "Type cancel to cancel", (Player player, String remove) -> {
							ArrayList<String> current = EditGUIValueList.this.toStringList(getMeta(player, "Value"));
							current.remove(remove);
							setMeta(player, "Value", current);
							applyValue(player, current, newValue -> setValue(player, new ArrayList<>(newValue)));
							sendMessage(player, "&cRemoved " + remove + " from " + getKey());
						});
			}
		};
	}

	public abstract void setValue(Player player, ArrayList<String> value);
}
