package com.Ben12345rocks.AdvancedCore.Util.Request;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookManager;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookSign;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptManager;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptReturnString;

/**
 * The Class RequestManager.
 */
public class RequestManager {

	/**
	 * Instantiates a new request manager.
	 *
	 * @param player
	 *            the player
	 * @param method
	 *            the method
	 * @param listener
	 *            the listener
	 * @param promptText
	 *            the prompt text
	 * @param currentValue
	 *            the current value
	 */
	public RequestManager(Player player, InputMethod method,
			final InputListener listener, String promptText, String currentValue) {
		if (method.equals(InputMethod.Anvil)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.Anvil.toString())) {

			AInventory inv = new AInventory(player,
					new AInventory.AnvilClickEventHandler() {

						@Override
						public void onAnvilClick(AnvilClickEvent event) {
							Player player = event.getPlayer();
							if (event.getSlot() == AInventory.AnvilSlot.OUTPUT) {

								event.setWillClose(true);
								event.setWillDestroy(true);

								listener.onInput(player, event.getName());

							} else {
								event.setWillClose(false);
								event.setWillDestroy(false);
							}
						}
					});

			ItemStack item = new ItemStack(Material.NAME_TAG);
			item = Utils.getInstance().setName(item, "" + currentValue);
			ArrayList<String> lore = new ArrayList<String>();
			lore.add("&cRename item and take out to set value");
			lore.add("&cDoes not cost exp");
			item = Utils.getInstance().addLore(item, lore);

			inv.setSlot(AInventory.AnvilSlot.INPUT_LEFT, item);

			inv.open();

		} else if (method.equals(InputMethod.Chat)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.Chat.toString())) {
			ConversationFactory convoFactory = new ConversationFactory(
					Main.plugin).withModality(true)
					.withEscapeSequence("cancel").withTimeout(30);
			PromptManager prompt = new PromptManager(promptText
					+ " Current value: " + currentValue, convoFactory);
			prompt.stringPrompt((Conversable) player, new PromptReturnString() {

				@Override
				public void onInput(ConversationContext context,
						Conversable conversable, String input) {
					listener.onInput((Player) conversable, input);
				}
			});
		} else if (method.equals(InputMethod.Book)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.Book.toString())) {

			new BookManager(player, currentValue, new BookSign() {

				@Override
				public void onBookSign(Player player, String input) {
					listener.onInput(player, input);

				}
			});
		} else {
			player.sendMessage("Invalid method/disabled method, set method using /advancedcore SetRequestMethod (method)");
		}
	}

	/**
	 * The Enum InputMethod.
	 */
	public enum InputMethod {

		/** The Anvil. */
		Anvil,

		/** The Chat. */
		Chat,

		/** The Book. */
		Book;
	}

}
