package com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookManager;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookSign;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;

import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class BooleanRequester.
 */
public class BooleanRequester {

	/**
	 * Instantiates a new boolean requester.
	 */
	public BooleanRequester() {

	}

	/**
	 * Request.
	 *
	 * @param player
	 *            the player
	 * @param method
	 *            the method
	 * @param currentValue
	 *            the current value
	 * @param promptText
	 *            the prompt text
	 * @param listener
	 *            the listener
	 */
	public void request(Player player, InputMethod method, String currentValue, String promptText,
			BooleanListener listener) {
		if (AdvancedCoreHook.getInstance().getDisabledRequestMethods().contains(method.toString())) {
			player.sendMessage("Disabled method: " + method.toString());
		}
		if (method.equals(InputMethod.SIGN)) {
			method = InputMethod.INVENTORY;
		}
		if (method.equals(InputMethod.INVENTORY)) {

			BInventory inv = new BInventory("Click one of the following:");

			inv.addButton(inv.getNextSlot(),
					new BInventoryButton("True", new String[] {}, new ItemStack(Material.REDSTONE_BLOCK)) {

						@Override
						public void onClick(ClickEvent clickEvent) {
							listener.onInput(clickEvent.getPlayer(),
									Boolean.valueOf(clickEvent.getClickedItem().getItemMeta().getDisplayName()));

						}
					});
			inv.addButton(inv.getNextSlot(),
					new BInventoryButton("False", new String[] {}, new ItemStack(Material.IRON_BLOCK)) {

						@Override
						public void onClick(ClickEvent clickEvent) {
							listener.onInput(clickEvent.getPlayer(),
									Boolean.valueOf(clickEvent.getClickedItem().getItemMeta().getDisplayName()));

						}
					});

			inv.openInventory(player);

		} else if (method.equals(InputMethod.ANVIL)) {

			AInventory inv = new AInventory(player, new AInventory.AnvilClickEventHandler() {

				@Override
				public void onAnvilClick(AnvilClickEvent event) {
					Player player = event.getPlayer();
					if (event.getSlot() == AInventory.AnvilSlot.OUTPUT) {

						event.setWillClose(true);
						event.setWillDestroy(true);

						listener.onInput(player, Boolean.valueOf(event.getName()));

					} else {
						event.setWillClose(false);
						event.setWillDestroy(false);
					}
				}
			});

			ItemBuilder builder = new ItemBuilder(Material.NAME_TAG);
			builder.setName(currentValue);

			ArrayList<String> lore = new ArrayList<String>();
			lore.add("&cRename item and take out to set value");
			lore.add("&cDoes not cost exp");
			builder.setLore(lore);

			inv.setSlot(AInventory.AnvilSlot.INPUT_LEFT, builder.toItemStack(player));

			inv.open();

		} else if (method.equals(InputMethod.CHAT)) {

			User user = UserManager.getInstance().getUser(player);
			user.sendMessage("&cClick one of the following options below:");
			String option = "True";
			TextComponent comp = new TextComponent(option);
			PlayerUtils.getInstance().setPlayerMeta(player, "ValueRequestBoolean", listener);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND, "/"
					+ AdvancedCoreHook.getInstance().getPlugin().getName() + "valuerequestinput Boolean " + option));
			user.sendJson(comp);
			option = "False";
			comp = new TextComponent(option);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND, "/"
					+ AdvancedCoreHook.getInstance().getPlugin().getName() + "valuerequestinput Boolean " + option));
			user.sendJson(comp);
		} else if (method.equals(InputMethod.BOOK)) {

			new BookManager(player, currentValue, new BookSign() {

				@Override
				public void onBookSign(Player player, String input) {
					listener.onInput(player, Boolean.valueOf(input));

				}
			});
		} else {
			player.sendMessage("Invalid method/disabled method, change your request method");
		}
	}
}
