package com.bencodez.advancedcore.api.valuerequest.requesters;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory.AnvilClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.book.BookManager;
import com.bencodez.advancedcore.api.valuerequest.book.BookSign;
import com.bencodez.advancedcore.api.valuerequest.listeners.BooleanListener;
import com.bencodez.advancedcore.nms.NMSManager;

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
	 * @param player       the player
	 * @param method       the method
	 * @param currentValue the current value
	 * @param promptText   the prompt text
	 * @param listener     the listener
	 */
	public void request(Player player, InputMethod method, String currentValue, String promptText,
			BooleanListener listener) {
		if (AdvancedCorePlugin.getInstance().getOptions().getDisabledRequestMethods().contains(method.toString())) {
			player.sendMessage("Disabled method: " + method.toString());
		}
		if (method.equals(InputMethod.SIGN)) {
			method = InputMethod.INVENTORY;
		}
		if (NMSManager.getInstance().isVersion("1.17") && method.equals(InputMethod.ANVIL)) {
			method = InputMethod.CHAT;
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

			AdvancedCoreUser user = UserManager.getInstance().getUser(player);
			user.sendMessage("&cClick one of the following options below:");
			String option = "True";
			TextComponent comp = new TextComponent(option);
			PlayerUtils.getInstance().setPlayerMeta(player, "ValueRequestBoolean", listener);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
					"/" + AdvancedCorePlugin.getInstance().getName() + "valuerequestinput Boolean " + option));
			user.sendJson(comp);
			option = "False";
			comp = new TextComponent(option);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
					"/" + AdvancedCorePlugin.getInstance().getName() + "valuerequestinput Boolean " + option));
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
