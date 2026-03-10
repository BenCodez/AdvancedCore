package com.bencodez.advancedcore.api.valuerequest.requesters;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.book.BookManager;
import com.bencodez.advancedcore.api.valuerequest.book.BookSign;
import com.bencodez.advancedcore.api.valuerequest.listeners.BooleanListener;
import com.bencodez.simpleapi.player.PlayerUtils;

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
		// Add support for the new DIALOG method using SimpleAPI's UniDialogService
		// Handle dialog input before converting methods or presenting other UIs
		if (method.equals(InputMethod.DIALOG)) {
			// Attempt to retrieve the dialog service from the plugin
			com.bencodez.simpleapi.dialog.UniDialogService dialogService = AdvancedCorePlugin.getInstance()
					.getDialogService();
			if (dialogService != null) {
				// Build a confirmation dialog for boolean input. The prompt text becomes the
				// title
				// and the current value is displayed in the body for context.
				String title = (promptText != null && !promptText.isEmpty()) ? promptText : "Select a value";
				String body = (currentValue != null && !currentValue.isEmpty()) ? ("Current value: " + currentValue)
						: "";
				dialogService.confirmation(player).title(title).body(body).yesText("True").noText("False")
						.onYes(payload -> {
							// When the user clicks the yes button, invoke the listener with true
							listener.onInput(player, true);
						}).onNo(payload -> {
							// When the user clicks the no button, invoke the listener with false
							listener.onInput(player, false);
						}).open();
			} else {
				// Dialog service unavailable, gracefully fall back to chat input
				new BooleanRequester().request(player, InputMethod.CHAT, currentValue, promptText, listener);
			}
			return;
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

		} else if (method.equals(InputMethod.CHAT)) {

			AdvancedCoreUser user = AdvancedCorePlugin.getInstance().getUserManager().getUser(player);
			user.sendMessage("&cClick one of the following options below:");
			String option = "True";
			TextComponent comp = new TextComponent(option);
			PlayerUtils.setPlayerMeta(AdvancedCorePlugin.getInstance(), player, "ValueRequestBoolean", listener);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
					"/" + AdvancedCorePlugin.getInstance().getName().toLowerCase() + "valuerequestinput Boolean "
							+ option));
			user.sendJson(comp);
			option = "False";
			comp = new TextComponent(option);
			comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
					"/" + AdvancedCorePlugin.getInstance().getName().toLowerCase() + "valuerequestinput Boolean "
							+ option));
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