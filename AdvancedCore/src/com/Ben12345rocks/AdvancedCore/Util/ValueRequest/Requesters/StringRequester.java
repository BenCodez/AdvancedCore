package com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters;

import java.util.ArrayList;

import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Material;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory;
import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookManager;
import com.Ben12345rocks.AdvancedCore.Util.Book.BookSign;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptManager;
import com.Ben12345rocks.AdvancedCore.Util.Prompt.PromptReturnString;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

public class StringRequester {

	public StringRequester() {

	}

	public void request(Player player, InputMethod method, String currentValue,
			String promptText, String[] options, boolean allowCustomOption,
			StringListener listener) {
		if (options == null && method.equals(InputMethod.INVENTORY)
				&& allowCustomOption) {
			method = InputMethod.ANVIL;
		}
		if (options != null && method.equals(InputMethod.ANVIL)) {
			method = InputMethod.INVENTORY;
		}
		if (method.equals(InputMethod.INVENTORY)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.INVENTORY.toString())) {
			if (options == null) {
				player.sendMessage("There are no choices to choice from to use this method");
				return;
			}

			BInventory inv = new BInventory("Click one of the following:");
			for (String str : options) {
				inv.addButton(inv.getNextSlot(), new BInventoryButton(str,
						new String[] {}, new ItemStack(Material.STONE)) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						listener.onInput(clickEvent.getPlayer(), clickEvent
								.getClickedItem().getItemMeta()
								.getDisplayName());

					}
				});
			}

			if (allowCustomOption) {
				inv.addButton(inv.getNextSlot(), new BInventoryButton(
						"&cClick to enter custom value", new String[] {},
						new ItemStack(Material.ANVIL)) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						new ValueRequest().requestString(
								clickEvent.getPlayer(), listener);
					}
				});
			}

			inv.openInventory(player);

		} else if (method.equals(InputMethod.ANVIL)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.ANVIL.toString())) {

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

		} else if (method.equals(InputMethod.CHAT)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.CHAT.toString())) {

			if (options != null) {
				User user = new User(Main.plugin, player);
				user.sendMessage("&cClick one of the following options below:");
				Utils.getInstance().setPlayerMeta(player,
						"ValueRequestString", listener);
				for (String option : options) {
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
							Action.RUN_COMMAND,
							"/advancedcore ValueRequestString " + option));
					user.sendJson(comp);
				}
				if (allowCustomOption) {
					String option = "CustomValue";
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
							Action.RUN_COMMAND,
							"/advancedcore ValueRequestString " + option));
					user.sendJson(comp);
				}
			} else {
				ConversationFactory convoFactory = new ConversationFactory(
						Main.plugin).withModality(true)
						.withEscapeSequence("cancel").withTimeout(60);
				PromptManager prompt = new PromptManager(promptText
						+ " Current value: " + currentValue, convoFactory);
				prompt.stringPrompt(player, new PromptReturnString() {

					@Override
					public void onInput(ConversationContext context,
							Conversable conversable, String input) {
						listener.onInput((Player) conversable, input);
					}
				});
			}
		} else if (method.equals(InputMethod.BOOK)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.BOOK.toString())) {

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
}
