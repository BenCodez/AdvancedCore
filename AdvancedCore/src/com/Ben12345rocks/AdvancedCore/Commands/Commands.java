package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Request.InputListener;
import com.Ben12345rocks.AdvancedCore.Util.Request.RequestManager;

// TODO: Auto-generated Javadoc
/**
 * The Class Commands.
 */
public class Commands {

	/** The instance. */
	static Commands instance = new Commands();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Commands.
	 *
	 * @return single instance of Commands
	 */
	public static Commands getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new commands.
	 */
	private Commands() {
	}

	/**
	 * Instantiates a new commands.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Commands(Main plugin) {
		Commands.plugin = plugin;
	}

	/**
	 * Open config GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openConfigGUI(Player player) {
		BInventory inv = new BInventory("Config");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Debug",
				new String[] { "Currently: "
						+ Config.getInstance().getDebugEnabled() },
						new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				User user = new User(Main.plugin, player);
				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Config.getInstance().setDebugEnabled(
								Boolean.valueOf(input));
						player.sendMessage("Value set");

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + Config.getInstance().getDebugEnabled());

			}
		});
		inv.addButton(inv.getNextSlot(), new BInventoryButton("DebugInGame",
				new String[] { "Currently: "
						+ Config.getInstance().getDebugInfoIngame() },
						new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				User user = new User(Main.plugin, player);
				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Config.getInstance().setDebugInfoIngame(
								Boolean.valueOf(input));
						player.sendMessage("Value set");

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + Config.getInstance().getDebugInfoIngame());

			}
		});
		inv.openInventory(player);
	}

	/**
	 * Open GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openGUI(Player player) {
		BInventory inv = new BInventory("AdminGUI");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cRewards",
				new String[] { "&cMiddle click to create" }, new ItemStack(
						Material.DIAMOND)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				if (event.getClick().equals(ClickType.MIDDLE)) {
					User user = new User(Main.plugin, player);
					new RequestManager(
							player,
							user.getInputMethod(),
							new InputListener() {

								@Override
								public void onInput(Player player, String input) {
									ConfigRewards.getInstance().getData(input);
									player.sendMessage("Reward file created");
									plugin.reload();

								}
							}

							,
							"Type value in chat to send, cancel by typing cancel",
							"");
				} else {
					openRewardsGUI(player);
				}
			}
		});
		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cConfig",
				new String[] {}, new ItemStack(Material.PAPER)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				openConfigGUI(player);

			}

		});
		inv.openInventory(player);
	}

	/**
	 * Open reward GUI.
	 *
	 * @param player
	 *            the player
	 * @param rewardName
	 *            the reward name
	 */
	public void openRewardGUI(Player player, String rewardName) {
		Reward reward = ConfigRewards.getInstance().getReward(rewardName);
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		User user = new User(Main.plugin, player);

		Utils.getInstance().setPlayerMeta(player, "Reward",
				reward.getRewardName());

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Execute",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new RequestManager(
						player,
						user.getInputMethod(),
						new InputListener() {

							@Override
							public void onInput(Player player, String input) {
								Reward reward = ConfigRewards.getInstance()
										.getReward(
												(String) Utils.getInstance()
												.getPlayerMeta(player,
														"Reward"));

								ConfigRewards
								.getInstance()
								.getReward(reward.getRewardName())
								.giveReward(
										new User(Main.plugin, input),
										Utils.getInstance()
										.isPlayerOnline(input));
								player.sendMessage("Ran Reward file");
								plugin.reload();

							}
						}

						,
						"Type value in chat to send, cancel by typing cancel",
						"");

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetChance",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setChance(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set Chance");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getChance());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setMoney(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set money");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getMoney());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMinMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setMinMoney(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set minmoney");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getMinMoney());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMaxMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setMaxMoney(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set maxmoney");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getMaxMoney());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setEXP(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set Exp");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getExp());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMinExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setMinExp(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set minExp");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getMaxExp());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMaxExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");
						if (Utils.getInstance().isInt(input)) {
							ConfigRewards.getInstance().setMaxExp(
									reward.getRewardName(),
									Integer.parseInt(input));
							player.sendMessage("Set maxExp");
							plugin.reload();
						} else {
							player.sendMessage("Must be an interger");
						}

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getMinExp());

			}

		});

		ArrayList<String> lore = new ArrayList<String>();
		lore.add("&cAdd current item inhand");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Add item", Utils
				.getInstance().convertArray(lore),
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = (Reward) Utils.getInstance().getPlayerMeta(
						player, "Reward");
				Player player = event.getWhoClicked();

				String rewardName = reward.getRewardName();
				@SuppressWarnings("deprecation")
				ItemStack item = player.getItemInHand();
				if (item != null && !item.getType().equals(Material.AIR)) {
					String material = item.getType().toString();
					@SuppressWarnings("deprecation")
					int data = item.getData().getData();
					int amount = item.getAmount();
					int durability = item.getDurability();
					String name = item.getItemMeta().getDisplayName();
					ArrayList<String> lore = (ArrayList<String>) item
							.getItemMeta().getLore();
					Map<Enchantment, Integer> enchants = item.getEnchantments();
					String itemStack = material;
					ConfigRewards.getInstance().setItemAmount(rewardName,
							itemStack, amount);
					ConfigRewards.getInstance().setItemData(rewardName,
							itemStack, data);
					ConfigRewards.getInstance().setItemMaterial(rewardName,
							itemStack, material);
					ConfigRewards.getInstance().setItemName(rewardName,
							itemStack, name);
					ConfigRewards.getInstance().setItemLore(rewardName,
							itemStack, lore);
					ConfigRewards.getInstance().setItemDurability(rewardName,
							itemStack, durability);
					for (Entry<Enchantment, Integer> entry : enchants
							.entrySet()) {
						ConfigRewards.getInstance().setItemEnchant(rewardName,
								itemStack, entry.getKey().getName(),
								entry.getValue().intValue());
					}
					plugin.reload();
				}
			}

		});

		lore = new ArrayList<String>();
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Remove item",
				Utils.getInstance().convertArray(lore), new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = (Reward) Utils.getInstance().getPlayerMeta(
						player, "Reward");
				Player player = event.getWhoClicked();
				String rewardName = reward.getRewardName();
				BInventory inv = new BInventory("RewardRemoveItem: "
						+ rewardName);

				int slot = 0;
				for (String item : reward.getItems()) {
					ItemStack itemStack = new ItemStack(Material.valueOf(reward
							.getItemMaterial().get(item)), reward
							.getItemAmount(item), Short.valueOf(Integer
									.toString(reward.getItemData().get(item))));
					String name = reward.getItemName().get(item);
					if (name != null) {
						itemStack = Utils.getInstance().nameItem(itemStack,
								name.replace("%Player%", user.getPlayerName()));
					}
					itemStack = Utils.getInstance().addLore(
							itemStack,
							Utils.getInstance().replace(
									reward.getItemLore().get(item), "%Player%",
									user.getPlayerName()));
					itemStack = Utils.getInstance().addEnchants(itemStack,
							reward.getItemEnchants().get(item));
					itemStack = Utils.getInstance().setDurabilty(itemStack,
							reward.getItemDurabilty().get(item));
					String skull = reward.getItemSkull().get(item);
					if (skull != null) {
						itemStack = Utils.getInstance()
								.setSkullOwner(
										itemStack,
										skull.replace("%Player%",
												user.getPlayerName()));
					}
					inv.addButton(slot, new BInventoryButton(item,
							new String[0], itemStack) {

						@Override
						public void onClick(ClickEvent event) {
							if (event.getWhoClicked() instanceof Player) {
								Player player = event.getWhoClicked();
								String item = event.getCurrentItem()
										.getItemMeta().getDisplayName();
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");
								ConfigRewards.getInstance().set(
										reward.getRewardName(),
										"Items." + item, null);
								player.closeInventory();
								player.sendMessage("Removed item");
								plugin.reload();

							}

						}
					});
					slot++;
				}

				inv.openInventory(player);

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMessage",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");

						ConfigRewards.getInstance().setMessagesReward(
								reward.getRewardName(), input);
						player.sendMessage("Set message");
						plugin.reload();

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.getRewardMsg());

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"AddConsoleCommand", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");

						ArrayList<String> commands = ConfigRewards
								.getInstance().getCommandsConsole(
										reward.getRewardName());
						commands.add(input);

						ConfigRewards.getInstance().setCommandsConsole(
								reward.getRewardName(), commands);
						player.sendMessage("Added console command");
						plugin.reload();

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
						"");

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"AddPlayerCommand", new String[0],
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");

						ArrayList<String> commands = ConfigRewards
								.getInstance().getCommandsPlayer(
										reward.getRewardName());
						commands.add(input);

						ConfigRewards.getInstance().setCommandsPlayer(
								reward.getRewardName(), commands);
						player.sendMessage("Added player command");
						plugin.reload();

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
						"");

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"RemoveConsoleCommand", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = (Reward) Utils.getInstance().getPlayerMeta(
						player, "Reward");
				Player player = event.getWhoClicked();

				BInventory inv = new BInventory("RemoveConsoleCommand: "
						+ reward.getRewardName());
				int count = 0;
				for (String cmd : ConfigRewards.getInstance()
						.getCommandsConsole(reward.getRewardName())) {
					inv.addButton(count, new BInventoryButton(cmd,
							new String[0], new ItemStack(Material.STONE)) {

						@Override
						public void onClick(ClickEvent event) {
							Reward reward = (Reward) Utils.getInstance()
									.getPlayerMeta(player, "Reward");
							Player player = event.getWhoClicked();

							ArrayList<String> commands = ConfigRewards
									.getInstance().getCommandsConsole(
											reward.getRewardName());
							if (event.getCurrentItem() != null
									&& !event.getCurrentItem().getType()
									.equals(Material.AIR)) {
								commands.remove(event.getCurrentItem()
										.getItemMeta().getDisplayName());
								ConfigRewards.getInstance().setCommandsConsole(
										reward.getRewardName(), commands);

							}
							player.closeInventory();
							player.sendMessage("Removed command");
							plugin.reload();

						}
					});
					count++;
				}

				inv.openInventory(player);

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"RemovePlayerCommand", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = (Reward) Utils.getInstance().getPlayerMeta(
						player, "Reward");
				Player player = event.getWhoClicked();

				BInventory inv = new BInventory("RemovePlayerCommand: "
						+ reward.getRewardName());
				int count = 0;
				for (String cmd : ConfigRewards.getInstance()
						.getCommandsPlayer(reward.getRewardName())) {
					inv.addButton(count, new BInventoryButton(cmd,
							new String[0], new ItemStack(Material.STONE)) {

						@Override
						public void onClick(ClickEvent event) {
							if (event.getWhoClicked() instanceof Player) {
								Player player = event.getWhoClicked();
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");
								ArrayList<String> commands = ConfigRewards
										.getInstance().getCommandsPlayer(
												reward.getRewardName());
								if (event.getCurrentItem() != null
										&& !event.getCurrentItem().getType()
										.equals(Material.AIR)) {
									commands.remove(event.getCurrentItem()
											.getItemMeta().getDisplayName());
									ConfigRewards.getInstance()
									.setCommandsPlayer(
											reward.getRewardName(),
											commands);

								}
								player.closeInventory();
								player.sendMessage("Removed command");
								plugin.reload();
							}
						}
					});
					count++;
				}

				inv.openInventory(player);

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"SetRequirePermission", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				User user = new User(Main.plugin, player);
				new RequestManager(player, user.getInputMethod(),
						new InputListener() {

					@Override
					public void onInput(Player player, String input) {
						Reward reward = (Reward) Utils.getInstance()
								.getPlayerMeta(player, "Reward");

						ConfigRewards.getInstance()
						.setRequirePermission(
								reward.getRewardName(),
								Boolean.valueOf(input));
						player.sendMessage("Set require permission");
						plugin.reload();

					}
				}

				,
				"Type value in chat to send, cancel by typing cancel",
				"" + reward.isRequirePermission());

			}

		});

		inv.openInventory(player);
	}

	/**
	 * Open rewards GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openRewardsGUI(Player player) {
		BInventory inv = new BInventory("Rewards");
		int count = 0;
		for (Reward reward : com.Ben12345rocks.AdvancedCore.Main.plugin.rewards) {
			ArrayList<String> lore = new ArrayList<String>();
			if (reward.isDelayEnabled()) {
				lore.add("DelayEnabled: true");
				lore.add("Delay: " + reward.getDelayHours() + ":"
						+ reward.getDelayMinutes());
			}
			if (reward.isTimedEnabled()) {
				lore.add("TimedEnabled: true");
				lore.add("Timed: " + reward.getTimedHour() + ":"
						+ reward.getTimedMinute());
			}
			if (reward.isRequirePermission()) {
				lore.add("RequirePermission: true");
			}
			if (reward.getWorlds().size() > 0) {
				lore.add("Worlds: "
						+ Utils.getInstance()
						.makeStringList(reward.getWorlds()));
				lore.add("GiveInEachWorld: " + reward.isGiveInEachWorld());
			}
			if (!reward.getRewardType().equals("BOTH")) {
				lore.add("RewardType: " + reward.getRewardType());
			}
			if (reward.getItems().size() > 0) {
				lore.add("Items:");
				for (String item : reward.getItems()) {
					lore.add(reward.getItemMaterial().get(item) + ":"
							+ reward.getItemData().get(item) + " "
							+ reward.getItemAmount().get(item));
				}
			}

			if (reward.getMoney() != 0) {
				lore.add("Money: " + reward.getMoney());
			}

			if (reward.getMaxMoney() != 0) {
				lore.add("MaxMoney: " + reward.getMaxMoney());
			}

			if (reward.getMinMoney() != 0) {
				lore.add("MinMoney: " + reward.getMinMoney());
			}

			if (reward.getExp() != 0) {
				lore.add("EXP: " + reward.getExp());
			}

			if (reward.getMaxExp() != 0) {
				lore.add("MaxEXP: " + reward.getMaxExp());
			}

			if (reward.getMinExp() != 0) {
				lore.add("MinEXP: " + reward.getMinExp());
			}

			if (reward.getConsoleCommands().size() > 0) {
				lore.add("ConsoleCommands:");
				lore.addAll(reward.getConsoleCommands());
			}
			if (reward.getPlayerCommands().size() > 0) {
				lore.add("PlayerCommands:");
				lore.addAll(reward.getPlayerCommands());
			}
			if (reward.getPotions().size() > 0) {
				lore.add("Potions:");
				for (String potion : reward.getPotions()) {
					lore.add(potion + " "
							+ reward.getPotionsDuration().get(potion) + " "
							+ reward.getPotionsAmplifier().get(potion));
				}
			}

			if (ConfigRewards.getInstance().getTitleEnabled(
					reward.getRewardName())) {
				lore.add("TitleEnabled: true");
				lore.add("TitleTitle: "
						+ ConfigRewards.getInstance().getTitleTitle(
								reward.getRewardName()));
				lore.add("TitleSubTitle: "
						+ ConfigRewards.getInstance().getTitleSubTitle(
								reward.getRewardName()));
				lore.add("Timings: "
						+ ConfigRewards.getInstance().getTitleFadeIn(
								reward.getRewardName())
								+ " "
								+ ConfigRewards.getInstance().getTitleShowTime(
										reward.getRewardName())
										+ " "
										+ ConfigRewards.getInstance().getTitleFadeOut(
												reward.getRewardName()));
			}

			if (reward.isBossBarEnabled()) {
				lore.add("BossBarEnabled: true");
				lore.add("BossBarMessage: " + reward.getBossBarMessage());
				lore.add("Color/Style/Progress/Delay: "
						+ reward.getBossBarColor() + "/"
						+ reward.getBossBarStyle() + "/"
						+ reward.getBossBarProgress() + "/"
						+ reward.getBossBarDelay());
			}
			if (ConfigRewards.getInstance().getSoundEnabled(
					reward.getRewardName())) {
				lore.add("SoundEnabled: true");
				lore.add("Sound/Volume/Pitch: "
						+ ConfigRewards.getInstance().getSoundSound(
								reward.getRewardName())
								+ "/"
								+ ConfigRewards.getInstance().getSoundVolume(
										reward.getRewardName())
										+ "/"
										+ ConfigRewards.getInstance().getSoundPitch(
												reward.getRewardName()));
			}

			if (ConfigRewards.getInstance().getEffectEnabled(
					reward.getRewardName())) {
				lore.add("EffectEnabled: true");
				lore.add("Effect/Data/Particles/Radius: "
						+ ConfigRewards.getInstance().getEffectEffect(
								reward.getRewardName())
								+ "/"
								+ ConfigRewards.getInstance().getEffectData(
										reward.getRewardName())
										+ "/"
										+ ConfigRewards.getInstance().getEffectParticles(
												reward.getRewardName())
												+ "/"
												+ ConfigRewards.getInstance().getEffectRadius(
														reward.getRewardName()));
			}

			if (reward.getActionBarMsg() != null) {
				lore.add("ActioBarMessage/Delay: " + reward.getActionBarMsg()
						+ "/" + reward.getActionBarDelay());
			}

			lore.add("MessagesReward: " + reward.getRewardMsg());

			inv.addButton(count, new BInventoryButton(reward.getRewardName(),
					Utils.getInstance().convertArray(lore), new ItemStack(
							Material.STONE)) {

				@Override
				public void onClick(ClickEvent event) {
					if (event.getWhoClicked() instanceof Player) {
						Player player = event.getWhoClicked();
						openRewardGUI(player, event.getCurrentItem()
								.getItemMeta().getDisplayName());
					}
				}
			});
			count++;
		}

		inv.openInventory(player);
	}
}
