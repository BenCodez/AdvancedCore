package com.bencodez.advancedcore.api.valuerequest.requesters;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.book.BookManager;
import com.bencodez.advancedcore.api.valuerequest.book.BookSign;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;
import com.bencodez.advancedcore.api.valuerequest.prompt.PromptManager;
import com.bencodez.advancedcore.api.valuerequest.prompt.PromptReturnString;
import com.bencodez.advancedcore.api.valuerequest.sign.SignMenu.InputReceiver;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.player.PlayerUtils;

import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class StringRequester.
 */
public class StringRequester {

	/**
	 * Instantiates a new string requester.
	 */
	public StringRequester() {

	}

	/**
	 * Request.
	 *
	 * @param player            the player
	 * @param method            the method
	 * @param currentValue      the current value
	 * @param promptText        the prompt text
	 * @param options           the options
	 * @param allowCustomOption the allow custom option
	 * @param listener          the listener
	 */
	public void request(Player player, InputMethod method, String currentValue,
			LinkedHashMap<String, ItemStack> options, String promptText, boolean allowCustomOption,
			StringListener listener) {
		if ((options == null || options.size() == 0) && method.equals(InputMethod.INVENTORY) && allowCustomOption) {
			method = InputMethod.CHAT;
		}
		if (AdvancedCorePlugin.getInstance().getOptions().getDisabledRequestMethods().contains(method.toString())) {
			player.sendMessage("Disabled method: " + method.toString());
		}
		if (method.equals(InputMethod.INVENTORY)) {
			if (options == null) {
				player.sendMessage("There are no choices to choice from to use this method");
				return;
			}

			BInventory inv = new BInventory("Click one of the following:");
			for (Entry<String, ItemStack> entry : options.entrySet()) {
				inv.addButton(inv.getNextSlot(),
						new BInventoryButton(entry.getKey(), new String[] {}, entry.getValue()) {

							@Override
							public void onClick(ClickEvent clickEvent) {
								listener.onInput(clickEvent.getPlayer(),
										clickEvent.getClickedItem().getItemMeta().getDisplayName());

							}
						});
			}

			if (allowCustomOption) {
				inv.addButton(inv.getNextSlot(), new BInventoryButton("&cClick to enter custom value", new String[] {},
						new ItemStack(Material.ANVIL)) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						new ValueRequest().requestString(clickEvent.getPlayer(), listener);
					}
				});
			}

			inv.openInventory(player);

		} else if (method.equals(InputMethod.CHAT)) {

			if (options != null && options.size() != 0) {
				AdvancedCoreUser user = AdvancedCorePlugin.getInstance().getUserManager().getUser(player);
				user.sendMessage("&cClick one of the following options below:");
				PlayerUtils.setPlayerMeta(AdvancedCorePlugin.getInstance(), player, "ValueRequestString", listener);
				for (String option : options.keySet()) {
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
							"/" + AdvancedCorePlugin.getInstance().getName().toLowerCase() + "valuerequestinput String "
									+ option));
					user.sendJson(comp);
				}
				if (allowCustomOption) {
					String option = "CustomValue";
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
							"/" + AdvancedCorePlugin.getInstance().getName().toLowerCase() + "valuerequestinput String "
									+ option));
					user.sendJson(comp);
				}
			} else {
				ConversationFactory convoFactory = new ConversationFactory(AdvancedCorePlugin.getInstance())
						.withModality(true).withEscapeSequence("cancel").withTimeout(60);
				PromptManager prompt = new PromptManager(promptText + " Current value: " + currentValue, convoFactory);
				prompt.stringPrompt(player, new PromptReturnString() {

					@Override
					public void onInput(ConversationContext context, Conversable conversable, String input) {
						AdvancedCorePlugin.getInstance().getBukkitScheduler()
								.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

									@Override
									public void run() {
										listener.onInput((Player) conversable, input);
									}
								});

					}
				});
			}
		} else if (method.equals(InputMethod.BOOK)) {

			new BookManager(player, currentValue, new BookSign() {

				@Override
				public void onBookSign(Player player, String input) {
					listener.onInput(player, input);

				}
			});
		} else if (method.equals(InputMethod.SIGN)) {
			AdvancedCorePlugin.getInstance().getSignMenu().open(player.getUniqueId(), new String[] { "", "", "", "" },
					new InputReceiver() {

						@Override
						public void receive(Player player, String[] text) {
							String str = "";
							for (String t : text) {
								str += t;
							}
							listener.onInput(player, str);

						}
					});
		} else {
			player.sendMessage("Invalid method/disabled method, change your request method");
		}
	}

	public void request(Player player, InputMethod method, String currentValue, String promptText, String[] options,
			boolean allowCustomOption, StringListener listener) {
		LinkedHashMap<String, ItemStack> items = new LinkedHashMap<>();
		if (options != null) {
			for (String option : options) {
				items.put(option, new ItemStack(Material.STONE, 1));
			}
		}
		items = ArrayUtils.sortByValuesStrItem(items);
		request(player, method, currentValue, items, promptText, allowCustomOption, listener);
	}
}
