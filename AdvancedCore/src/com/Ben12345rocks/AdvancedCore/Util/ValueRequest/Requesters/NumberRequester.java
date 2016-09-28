package com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters;

import java.util.ArrayList;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;

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
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;

public class NumberRequester {

	public NumberRequester() {

	}

	public void request(Player player, InputMethod method, String currentValue,
			String promptText, Number[] options, boolean allowCustomOption,
			NumberListener listener) {
		if (method.equals(InputMethod.INVENTORY)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.ANVIL.toString())) {
			if (options == null) {
				player.sendMessage("There are no choices to choice from to use this method");
				return;
			}

			BInventory inv = new BInventory("Click one of the following:");
			for (Number str : options) {
				inv.addButton(inv.getNextSlot(),
						new BInventoryButton(str.toString(), new String[] {},
								new ItemStack(Material.STONE)) {

							@Override
							public void onClick(ClickEvent clickEvent) {
								String num = clickEvent.getClickedItem()
										.getItemMeta().getDisplayName();
								try {
									Number number = (Double) Double
											.valueOf(num);
									listener.onInput(clickEvent.getPlayer(),
											number);
								} catch (NumberFormatException ex) {
									ex.printStackTrace();
								}

							}
						});
			}

			if (allowCustomOption) {
				inv.addButton(inv.getNextSlot(), new BInventoryButton(
						"&cClick to enter custom value", new String[] {},
						new ItemStack(Material.ANVIL)) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						new ValueRequest().requestNumber(
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

								String num = event.getName();
								try {
									Number number = (Double) Double
											.valueOf(num);
									listener.onInput(player, number);
								} catch (NumberFormatException ex) {
									ex.printStackTrace();
								}

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
				for (Number num : options) {
					String option = num.toString();
					TextComponent comp = new TextComponent(option);
					Utils.getInstance().setPlayerMeta(player,
							"ValueRequestNumber", listener);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
							Action.RUN_COMMAND,
							"advancedcore ValueRequestNumber " + option));
					user.sendJson(comp);
				}
				if (allowCustomOption) {
					String option = "CustomValue";
					TextComponent comp = new TextComponent(option);
					Utils.getInstance().setPlayerMeta(player,
							"ValueRequestNumber", listener);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
							Action.RUN_COMMAND,
							"advancedcore ValueRequestNumber " + option));
					user.sendJson(comp);
				}
			}
			ConversationFactory convoFactory = new ConversationFactory(
					Main.plugin).withModality(true)
					.withEscapeSequence("cancel").withTimeout(60);
			PromptManager prompt = new PromptManager(promptText
					+ " Current value: " + currentValue, convoFactory);
			prompt.stringPrompt(player, new PromptReturnString() {

				@Override
				public void onInput(ConversationContext context,
						Conversable conversable, String input) {
					String num = input;
					try {
						Number number = (Double) Double.valueOf(num);
						listener.onInput((Player) conversable, number);
					} catch (NumberFormatException ex) {
						ex.printStackTrace();
					}
				}
			});
		} else if (method.equals(InputMethod.BOOK)
				&& !Config.getInstance().getRequestAPIDisabledMethods()
						.contains(InputMethod.BOOK.toString())) {

			new BookManager(player, currentValue.toString(), new BookSign() {

				@Override
				public void onBookSign(Player player, String input) {
					String num = input;
					try {
						Number number = (Double) Double.valueOf(num);
						listener.onInput(player, number);
					} catch (NumberFormatException ex) {
						ex.printStackTrace();
					}

				}
			});
		} else {
			player.sendMessage("Invalid method/disabled method, set method using /advancedcore SetRequestMethod (method)");
		}

	}
}
