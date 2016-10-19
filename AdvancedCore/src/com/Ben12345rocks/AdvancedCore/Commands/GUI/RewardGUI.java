package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

public class RewardGUI {

	static RewardGUI instance = new RewardGUI();

	/** The plugin. */
	Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Commands.
	 *
	 * @return single instance of Commands
	 */
	public static RewardGUI getInstance() {
		return instance;
	}

	private void setCurrentReward(Player player, Reward reward) {
		Utils.getInstance().setPlayerMeta(player, "Reward", reward);
	}

	public Reward getCurrentReward(Player player) {
		return (Reward) Utils.getInstance().getPlayerMeta(player, "Reward");
	}

	/**
	 * Instantiates a new commands.
	 */
	private RewardGUI() {
	}

	public void openRewardGUIExp(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);
				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getExp()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");

								ConfigRewards.getInstance().setEXP(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set Exp to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMinExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);

				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getMinExp()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = getCurrentReward(player);

								ConfigRewards.getInstance().setMinExp(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set MinExp to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMaxExp",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();
				Reward reward = getCurrentReward(player);

				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getMaxExp()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = getCurrentReward(player);

								ConfigRewards.getInstance().setMaxExp(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set MaxExp to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		inv.openInventory(player);
	}

	public void openRewardGUIMoney(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);

				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getMoney()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = getCurrentReward(player);

								ConfigRewards.getInstance().setMoney(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set oney to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMinMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);
				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getMinMoney()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = getCurrentReward(player);

								ConfigRewards.getInstance().setMinMoney(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set MinMoney to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("SetMaxMoney",
				new String[0], new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);
				new ValueRequest().requestNumber(player,
						Integer.toString(reward.getMaxMoney()), null,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = getCurrentReward(player);

								ConfigRewards.getInstance().setMaxMoney(
										reward.getRewardName(),
										value.intValue());
								player.sendMessage("Set MaxMoney to "
										+ value.intValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});
		inv.openInventory(player);
	}

	public void openRewardGUIItems(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("&cAdd current item inhand");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Add item", Utils
				.getInstance().convertArray(lore),
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = getCurrentReward(player);
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
				Reward reward = getCurrentReward(player);
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
								name.replace("%Player%", player.getName()));
					}
					itemStack = Utils.getInstance().addLore(
							itemStack,
							Utils.getInstance().replace(
									reward.getItemLore().get(item), "%Player%",
									player.getName()));
					itemStack = Utils.getInstance().addEnchants(itemStack,
							reward.getItemEnchants().get(item));
					itemStack = Utils.getInstance().setDurabilty(itemStack,
							reward.getItemDurabilty().get(item));
					String skull = reward.getItemSkull().get(item);
					if (skull != null) {
						itemStack = Utils.getInstance().setSkullOwner(
								itemStack,
								skull.replace("%Player%", player.getName()));
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

		inv.openInventory(player);
	}

	public void openRewardGUICommands(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"AddConsoleCommand", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new ValueRequest().requestString(player, new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						Reward reward = getCurrentReward(player);

						ArrayList<String> commands = ConfigRewards
								.getInstance().getCommandsConsole(
										reward.getRewardName());
						commands.add(value);

						ConfigRewards.getInstance().setCommandsConsole(
								reward.getRewardName(), commands);
						player.sendMessage("Added console command");
						plugin.reload();

					}
				});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"AddPlayerCommand", new String[0],
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {

				Player player = event.getWhoClicked();
				new ValueRequest().requestString(player, new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						Reward reward = getCurrentReward(player);

						ArrayList<String> commands = ConfigRewards
								.getInstance().getCommandsPlayer(
										reward.getRewardName());
						commands.add(value);

						ConfigRewards.getInstance().setCommandsPlayer(
								reward.getRewardName(), commands);
						player.sendMessage("Added player command");
						plugin.reload();

					}
				});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"RemoveConsoleCommand", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Reward reward = getCurrentReward(player);
				Player player = event.getWhoClicked();

				BInventory inv = new BInventory("RemoveConsoleCommand: "
						+ reward.getRewardName());
				int count = 0;
				new ValueRequest().requestString(
						player,
						"",
						Utils.getInstance().convertArray(
								ConfigRewards.getInstance().getCommandsConsole(
										reward.getRewardName())), false,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");
								ArrayList<String> commands = ConfigRewards
										.getInstance().getCommandsConsole(
												reward.getRewardName());
								if (event.getCurrentItem() != null
										&& !event.getCurrentItem().getType()
												.equals(Material.AIR)) {
									commands.remove(event.getCurrentItem()
											.getItemMeta().getDisplayName());
									ConfigRewards.getInstance()
											.setCommandsConsole(
													reward.getRewardName(),
													commands);

								}
								player.closeInventory();
								player.sendMessage("Removed command");
								plugin.reload();
							}
						});
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
				Reward reward = getCurrentReward(player);
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

		inv.openInventory(player);
	}

	public void openRewardGUIPermission(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set Permission Required", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				Reward reward = getCurrentReward(player);
				new ValueRequest().requestBoolean(player,
						Boolean.toString(reward.isRequirePermission()),
						new BooleanListener() {

							@Override
							public void onInput(Player player, boolean value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");

								ConfigRewards.getInstance()
										.setRequirePermission(
												reward.getRewardName(), value);
								player.sendMessage("Permission Required set to "
										+ String.valueOf(value)
										+ " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});
			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Set Permssion",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getWhoClicked();
				Reward reward = getCurrentReward(player);
				new ValueRequest().requestString(
						player,
						reward.getPermission(),
						new String[] { "AdvancedCore.Reward."
								+ reward.getRewardName() }, true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");
								ConfigRewards.getInstance().setPermission(
										reward.getRewardName(), value);
								player.sendMessage("Permission set to " + value
										+ " on " + reward.getRewardName());
								plugin.reload();

							}
						});
			}
		});

		inv.openInventory(player);
	}

	public void openRewardGUIMessages(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set Reward Message", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new ValueRequest().requestString(player, new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						Reward reward = getCurrentReward(player);

						ConfigRewards.getInstance().setMessagesReward(
								reward.getRewardName(), value);
						player.sendMessage("Reward message set to " + value
								+ " on " + reward);
						plugin.reload();

					}
				});

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set Broadcast Message", new String[0], new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new ValueRequest().requestString(player, new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						Reward reward = getCurrentReward(player);

						ConfigRewards.getInstance().setMessagesBroadcast(
								reward.getRewardName(), value);
						player.sendMessage("Broadcast message set to " + value
								+ " on " + reward);
						plugin.reload();

					}
				});

			}

		});
		inv.openInventory(player);
	}

	public void openRewardGUIBasic(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);
		// edit chance
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set Give Chance", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();

				Reward reward = getCurrentReward(player);
				Number[] nums = new Number[101];
				for (int i = 0; i < nums.length; i++) {
					nums[i] = i;
				}
				new ValueRequest().requestNumber(player,
						Double.toString(reward.getChance()), nums,
						new NumberListener() {

							@Override
							public void onInput(Player player, Number value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");

								ConfigRewards.getInstance().setChance(
										reward.getRewardName(),
										value.doubleValue());
								player.sendMessage("Chance set to "
										+ value.doubleValue() + " on "
										+ reward.getRewardName());
								plugin.reload();

							}
						});

			}

		});

		// edit min/max/money
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Money",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUIMoney(clickEvent.getPlayer(), reward);
			}
		});

		// edit min/max/exp
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Exp",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUIExp(clickEvent.getPlayer(), reward);
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Items",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUIItems(clickEvent.getPlayer(), reward);
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Commands",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUICommands(clickEvent.getPlayer(), reward);
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Messages",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUIMessages(clickEvent.getPlayer(), reward);
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Edit Permissions", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				openRewardGUIPermission(clickEvent.getPlayer(), reward);
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
		Reward reward = RewardHandler.getInstance().getReward(rewardName);
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());

		setCurrentReward(player, reward);

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Basic Values",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getPlayer();
				openRewardGUIBasic(player, getCurrentReward(player));
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Advanced Values", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getPlayer();
				openRewardGUIAdvanced(player, getCurrentReward(player));
			}
		});

		inv.openInventory(player);
	}

	public void openRewardGUIAdvanced(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Edit Worlds",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getPlayer();
				openRewardGUIWorlds(player, getCurrentReward(player));

			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set Reward Type", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Reward reward = getCurrentReward(player);
				new ValueRequest().requestString(clickEvent.getPlayer(),
						reward.getRewardType(), new String[] { "BOTH",
								"OFFLINE", "ONLINE" }, false,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								Reward reward = getCurrentReward(player);
								ConfigRewards.getInstance().setRewardType(
										reward.getRewardName(), value);
								player.sendMessage("Set rewward type to "
										+ value + " on "
										+ reward.getRewardName());
								plugin.reload();
							}
						});
			}
		});

		inv.openInventory(player);
	}

	public void openRewardGUIWorlds(Player player, Reward reward) {
		BInventory inv = new BInventory("Reward: " + reward.getRewardName());
		setCurrentReward(player, reward);

		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Set GiveInEachWorld", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getWhoClicked();

				Reward reward = getCurrentReward(player);

				new ValueRequest().requestBoolean(player,
						"" + reward.isGiveInEachWorld(), new BooleanListener() {

							@Override
							public void onInput(Player player, boolean value) {
								Reward reward = (Reward) Utils.getInstance()
										.getPlayerMeta(player, "Reward");
								ConfigRewards.getInstance().setGiveInEachWorld(
										reward.getRewardName(), value);
								player.sendMessage("GiveInEachWorld set to "
										+ value + " on "
										+ reward.getRewardName());
								plugin.reload();
							}
						});

			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Add world",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getWhoClicked();

				ArrayList<String> worlds = new ArrayList<String>();
				for (World world : Bukkit.getWorlds()) {
					worlds.add(world.getName());
				}
				new ValueRequest().requestString(player, "", Utils
						.getInstance().convertArray(worlds), true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								Reward reward = getCurrentReward(player);
								ArrayList<String> worlds = ConfigRewards
										.getInstance().getWorlds(
												reward.getRewardName());
								worlds.add(value);
								ConfigRewards.getInstance().setWorlds(
										reward.getRewardName(), worlds);

								player.sendMessage("Added world " + value
										+ " on " + reward.getRewardName());

								plugin.reload();
							}
						});
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("Remove world",
				new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getWhoClicked();
				Reward reward = getCurrentReward(player);
				ArrayList<String> worlds = ConfigRewards.getInstance()
						.getWorlds(reward.getRewardName());

				new ValueRequest().requestString(player, "", Utils
						.getInstance().convertArray(worlds), true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								Reward reward = getCurrentReward(player);
								ArrayList<String> worlds = ConfigRewards
										.getInstance().getWorlds(
												reward.getRewardName());
								worlds.remove(value);
								ConfigRewards.getInstance().setWorlds(
										reward.getRewardName(), worlds);

								player.sendMessage("Removed world " + value
										+ " on " + reward.getRewardName());

								plugin.reload();
							}
						});
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
		for (Reward reward : RewardHandler.getInstance().getRewards()) {
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
			if (reward.getChance() != 0 && reward.getChance() != 100) {
				lore.add("Chance: " + reward.getChance());
			}
			if (reward.isRequirePermission()) {
				lore.add("RequirePermission: true");
				lore.add("Permssion: " + reward.getPermission());
			}
			if (reward.isJavascriptEnabled()) {
				lore.add("Javascript: true");
				lore.add("Expression: " + reward.getJavascriptExpression());
				lore.add("TrueRewards: "
						+ Utils.getInstance().makeStringList(
								reward.getJavascriptTrueRewards()));
				lore.add("FalseRewards: "
						+ Utils.getInstance().makeStringList(
								reward.getJavascriptFalseRewards()));
			}
			if (reward.isChoiceRewardsEnabled()) {
				lore.add("ChoiceRewards: true");
				lore.add("Rewards: "
						+ Utils.getInstance().makeStringList(
								reward.getChoiceRewardsRewards()));
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

			if (reward.isFireworkEnabled()) {
				lore.add("Firework: true");
				lore.add("Power: " + reward.getFireworkPower());
				lore.add("Colors: "
						+ Utils.getInstance().makeStringList(
								reward.getFireworkColors()));
				lore.add("FadeOutColors: "
						+ Utils.getInstance().makeStringList(
								reward.getFireworkFadeOutColors()));
				lore.add("Types: "
						+ Utils.getInstance().makeStringList(
								reward.getFireworkTypes()));
				lore.add("Trail: " + reward.isFireworkTrail());
				lore.add("Flicker: " + reward.isFireworkFlicker());
			}

			if (reward.getActionBarMsg() != null) {
				lore.add("ActioBarMessage/Delay: " + reward.getActionBarMsg()
						+ "/" + reward.getActionBarDelay());
			}

			if (!reward.getRewardMsg().equals("")) {
				lore.add("MessagesReward: " + reward.getRewardMsg());
			}
			if (!reward.getBroadcastMsg().equals("")) {
				lore.add("Broadcast: " + reward.getBroadcastMsg());
			}

			inv.addButton(count, new BInventoryButton(reward.getRewardName(),
					Utils.getInstance().convertArray(lore), new ItemStack(
							Material.STONE)) {

				@Override
				public void onClick(ClickEvent event) {
					Player player = event.getWhoClicked();
					openRewardGUI(player, event.getCurrentItem().getItemMeta()
							.getDisplayName());
				}
			});
			count++;
		}

		inv.openInventory(player);
	}

}
