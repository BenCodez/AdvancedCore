package com.bencodez.advancedcore.api.valuerequest.requesters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory;
import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory.AnvilClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.user.User;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.book.BookManager;
import com.bencodez.advancedcore.api.valuerequest.book.BookSign;
import com.bencodez.advancedcore.api.valuerequest.listeners.NumberListener;
import com.bencodez.advancedcore.api.valuerequest.prompt.PromptManager;
import com.bencodez.advancedcore.api.valuerequest.prompt.PromptReturnString;

import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The Class NumberRequester.
 */
public class NumberRequester {

	/**
	 * Instantiates a new number requester.
	 */
	public NumberRequester() {
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
			LinkedHashMap<Number, ItemStack> options, String promptText, boolean allowCustomOption,
			NumberListener listener) {
		if (method.equals(InputMethod.SIGN)) {
			method = InputMethod.INVENTORY;
		}
		if ((options == null || options.size() == 0) && method.equals(InputMethod.INVENTORY) && allowCustomOption) {
			method = InputMethod.ANVIL;
		}
		if ((options != null && options.size() != 0) && method.equals(InputMethod.ANVIL)) {
			method = InputMethod.INVENTORY;
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
			for (Entry<Number, ItemStack> entry : options.entrySet()) {
				inv.addButton(inv.getNextSlot(),
						new BInventoryButton(entry.getKey().toString(), new String[] {}, entry.getValue()) {

							@Override
							public void onClick(ClickEvent clickEvent) {
								String num = clickEvent.getClickedItem().getItemMeta().getDisplayName();
								try {
									Number number = Double.valueOf(num);
									listener.onInput(clickEvent.getPlayer(), number);
								} catch (NumberFormatException ex) {
									ex.printStackTrace();
								}

							}
						});
			}

			if (allowCustomOption) {
				inv.addButton(inv.getNextSlot(), new BInventoryButton("&cClick to enter custom value", new String[] {},
						new ItemStack(Material.ANVIL)) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						new ValueRequest().requestNumber(clickEvent.getPlayer(), listener);
					}
				});
			}

			inv.openInventory(player);

		} else if (method.equals(InputMethod.ANVIL)) {

			AInventory inv = new AInventory(player, new AInventory.AnvilClickEventHandler() {

				@Override
				public void onAnvilClick(AnvilClickEvent event) {
					Player player = event.getPlayer();
					if (event.getSlot() == AInventory.AnvilSlot.OUTPUT) {

						event.setWillClose(true);
						event.setWillDestroy(true);

						String num = event.getName();
						try {
							Number number = Double.valueOf(num);
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

			ItemBuilder builder = new ItemBuilder(Material.NAME_TAG);
			builder.setName(currentValue);

			ArrayList<String> lore = new ArrayList<String>();
			lore.add("&cRename item and take out to set value");
			lore.add("&cDoes not cost exp");
			builder.setLore(lore);

			inv.setSlot(AInventory.AnvilSlot.INPUT_LEFT, builder.toItemStack(player));

			inv.open();

		} else if (method.equals(InputMethod.CHAT)) {
			if (options != null && options.size() != 0) {
				User user = UserManager.getInstance().getUser(player);
				user.sendMessage("&cClick one of the following options below:");
				PlayerUtils.getInstance().setPlayerMeta(player, "ValueRequestNumber", listener);
				for (Number num : options.keySet()) {
					String option = num.toString();
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
							"/" + AdvancedCorePlugin.getInstance().getName() + "valuerequestinput Number " + option));
					user.sendJson(comp);
				}
				if (allowCustomOption) {
					String option = "CustomValue";
					TextComponent comp = new TextComponent(option);
					comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(Action.RUN_COMMAND,
							"/" + AdvancedCorePlugin.getInstance().getName() + "valuerequestinput Number " + option));
					user.sendJson(comp);
				}
			} else {
				ConversationFactory convoFactory = new ConversationFactory(AdvancedCorePlugin.getInstance())
						.withModality(true).withEscapeSequence("cancel").withTimeout(60);
				PromptManager prompt = new PromptManager(promptText + " Current value: " + currentValue, convoFactory);
				prompt.stringPrompt(player, new PromptReturnString() {

					@Override
					public void onInput(ConversationContext context, Conversable conversable, String input) {
						String num = input;
						try {
							Number number = Double.valueOf(num);
							Bukkit.getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
									new Runnable() {

										@Override
										public void run() {
											listener.onInput((Player) conversable, number);
										}
									});
						} catch (NumberFormatException ex) {
							ex.printStackTrace();
						}
					}
				});
			}
		} else if (method.equals(InputMethod.BOOK)) {

			new BookManager(player, currentValue.toString(), new BookSign() {

				@Override
				public void onBookSign(Player player, String input) {
					String num = input;
					try {
						Number number = Double.valueOf(num);
						listener.onInput(player, number);
					} catch (NumberFormatException ex) {
						ex.printStackTrace();
					}

				}
			});
		} else {
			player.sendMessage("Invalid method/disabled method, change your request method");
		}

	}

	public void request(Player player, InputMethod method, String currentValue, String promptText, Number[] options,
			boolean allowCustomOption, NumberListener listener) {
		LinkedHashMap<Number, ItemStack> items = new LinkedHashMap<Number, ItemStack>();
		if (options != null) {
			for (Number option : options) {
				items.put(option, new ItemStack(Material.STONE, 1));
			}
		}
		request(player, method, currentValue, items, promptText, allowCustomOption, listener);
	}
}
