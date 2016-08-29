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
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptManager;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptReturnString;

public class RequestManager {

	public RequestManager(Conversable conversable, InputMethod method,
			final InputListener listener, String promptText, String currentValue) {
		if (method.equals(InputMethod.Anvil)) {
			if (conversable instanceof Player) {
				AInventory inv = new AInventory((Player) conversable,
						new AInventory.AnvilClickEventHandler() {

							@Override
							public void onAnvilClick(AnvilClickEvent event) {
								Player player = event.getPlayer();
								if (event.getSlot() == AInventory.AnvilSlot.OUTPUT) {

									event.setWillClose(true);
									event.setWillDestroy(true);

									listener.onInput((Conversable) player,
											event.getName());

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

			} else {
				Main.plugin.getLogger().info("Must be a player to use this");
			}
		} else if (method.equals(InputMethod.Conversable)) {
			ConversationFactory convoFactory = new ConversationFactory(
					Main.plugin).withModality(true)
					.withEscapeSequence("cancel").withTimeout(20);
			PromptManager prompt = new PromptManager(promptText
					+ " Current value: " + currentValue, convoFactory);
			prompt.stringPrompt(conversable, new PromptReturnString() {

				@Override
				public void onInput(ConversationContext context,
						Conversable conversable, String input) {
					listener.onInput(conversable, input);
				}
			});
		}
	}

	public enum InputMethod {
		Anvil,

		Conversable;
	}

}
