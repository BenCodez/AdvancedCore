package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;

import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.RewardGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.UserGUI;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Report.Report;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.InputMethod;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandLoader.
 */
public class CommandLoader {

	/** The plugin. */
	Main plugin = Main.plugin;

	/**
	 * Load commands.
	 */
	public void loadCommands() {
		plugin.advancedCoreCommands = new ArrayList<CommandHandler>();
		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "Reload" }, "AdvancedCore.Reload",
				"Reload the plugin") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				plugin.reload();
				sender.sendMessage(Utils.getInstance().colorize(
						"&c" + plugin.getName() + " v"
								+ plugin.getDescription().getVersion()
								+ " reloaded"));
			}
		});
		plugin.advancedCoreCommands
				.add(new CommandHandler(new String[] { "Help" },
						"AdvancedCore.Help", "View this page") {

					@Override
					public void execute(CommandSender sender, String[] args) {
						ArrayList<TextComponent> msg = new ArrayList<TextComponent>();
						msg.add(Utils.getInstance().stringToComp(
								"&c" + plugin.getName() + " help"));
						for (CommandHandler cmdHandle : plugin.advancedCoreCommands) {
							msg.add(cmdHandle.getHelpLine("/advancedcore"));
						}
						if (sender instanceof Player) {
							new User(plugin, (Player) sender).sendJson(msg);
						} else {
							sender.sendMessage(Utils.getInstance()
									.convertArray(
											Utils.getInstance().comptoString(
													msg)));
						}
					}
				});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"SetRequestMethod", "(RequestMethod)" },
				"AdvancedCore.SetRequestMethod", "SetRequestMethod") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (sender instanceof Player) {
					User user = new User(Main.plugin, (Player) sender);
					user.setUserInputMethod(InputMethod.valueOf(args[1]));
				}
			}
		});
		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "Perms" }, "AdvancedCore.Perms",
				"View permissions list") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ArrayList<String> msg = new ArrayList<String>();
				msg.add("&c" + plugin.getName() + " permissions");
				for (CommandHandler cmdHandle : plugin.advancedCoreCommands) {
					msg.add(cmdHandle.getPerm());
				}
				if (sender instanceof Player) {
					new User(plugin, (Player) sender).sendMessage(msg);
				} else {
					sender.sendMessage(Utils.getInstance().convertArray(msg));
				}
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "GUI" }, "AdvancedCore.GUI", "Open GUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Commands.getInstance().openGUI((Player) sender);
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "Report" }, "AdvancedCore.Report",
				"Create Report File") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Report.getInstance().create();
				sender.sendMessage("Created zip file");
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "Rewards" }, "AdvancedCore.GUI",
				"Open RewardGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				RewardGUI.getInstance().openRewardsGUI((Player) sender);
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(
				new String[] { "Users" }, "AdvancedCore.GUI", "Open UserGUI",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUsersGUI((Player) sender);
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"Users", "(Player)" }, "AdvancedCore.GUI", "Open UserGUI",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUserGUI((Player) sender, args[1]);
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"Rewards", "(Reward)" }, "AdvancedCore.GUI", "Open GUI Reward",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				RewardGUI.getInstance().openRewardGUI((Player) sender, args[1]);
			}
		});

		plugin.advancedCoreCommands
				.add(new CommandHandler(new String[] { "GiveReward",
						"(Reward)", "(Player)" }, "AdvancedCore.GiveReward",
						"Give a player a reward file", true) {

					@Override
					public void execute(CommandSender sender, String[] args) {
						ConfigRewards
								.getInstance()
								.getReward(args[1])
								.giveReward(
										new User(plugin, args[2]),
										Utils.getInstance().isPlayerOnline(
												args[2]));
						sender.sendMessage("Gave " + args[2]
								+ " the reward file " + args[1]);
					}
				});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"SelectChoiceReward", "(Reward)" },
				"AdvancedCore.SelectChoiceReward",
				"Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = ConfigRewards.getInstance().getReward(args[1]);
				User user = new User(Main.plugin, (Player) sender);
				if (user.getChoiceReward(reward) != 0) {
					new ValueRequest(InputMethod.INVENTORY).requestString(
							(Player) sender,
							"",
							Utils.getInstance().convertArray(
									reward.getChoiceRewardsRewards()), false,
							new StringListener() {

								@Override
								public void onInput(Player player, String value) {
									User user = new User(Main.plugin, player);
									ConfigRewards.getInstance()
											.getReward(value)
											.giveReward(user, true);
									user.setChoiceReward(reward,
											user.getChoiceReward(reward) - 1);
								}
							});
				} else {
					sender.sendMessage("No rewards to choose");
				}
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"ValueRequestString", "(String)" },
				"AdvancedCore.ValueRequest", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					StringListener listener = (StringListener) Utils
							.getInstance().getPlayerMeta(player,
									"ValueRequestString");
					if (args[1].equals("CustomValue")) {
						new ValueRequest().requestString(player, listener);
					} else {
						listener.onInput(player, args[1]);
					}
				} catch (Exception ex) {
					player.sendMessage("No where to input value or error occured");
				}
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"ValueRequestNumber", "(Number)" },
				"AdvancedCore.ValueRequest", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					NumberListener listener = (NumberListener) Utils
							.getInstance().getPlayerMeta(player,
									"ValueRequestNumber");
					if (args[1].equals("CustomValue")) {
						new ValueRequest().requestNumber(player, listener);
					} else {
						Number number = (Double) Double.valueOf(args[1]);
						listener.onInput(player, number);
					}
				} catch (Exception ex) {
					player.sendMessage("No where to input value or error occured");
				}
			}
		});

		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"ValueRequestBoolean", "(Boolean)" },
				"AdvancedCore.ValueRequest", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					BooleanListener listener = (BooleanListener) Utils
							.getInstance().getPlayerMeta(player,
									"ValueRequestBoolean");
					listener.onInput(player, Boolean.valueOf(args[1]));
				} catch (Exception ex) {
					player.sendMessage("No where to input value");
				}
			}
		});

		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().run(
						new Runnable() {

							@Override
							public void run() {
								loadTabComplete();
								loadUserGUI();
							}
						});
			}
		});

	}

	private void loadUserGUI() {
		BInventory inv = new BInventory("AdvancedCore UserGUI");
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"Give Reward File", new String[] {}, new ItemStack(
						Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> rewards = new ArrayList<String>();
				for (Reward reward : plugin.rewards) {
					rewards.add(reward.getRewardName());
				}

				new ValueRequest().requestString(clickEvent.getPlayer(), "",
						Utils.getInstance().convertArray(rewards), true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								User user = new User(Main.plugin, UserGUI
										.getInstance().getCurrentPlayer(player));
								ConfigRewards.getInstance().getReward(value)
										.giveReward(user, user.isOnline());
								player.sendMessage("Given "
										+ user.getPlayerName()
										+ " reward file " + value);

							}
						});

			}
		});
		UserGUI.getInstance().addPluginButton(plugin, inv);
	}

	/**
	 * Load tab complete.
	 */
	public void loadTabComplete() {
		ArrayList<String> method = new ArrayList<String>();
		for (InputMethod me : InputMethod.values()) {
			method.add(me.toString());
		}
		for (int i = 0; i < plugin.advancedCoreCommands.size(); i++) {
			plugin.advancedCoreCommands.get(i).addTabCompleteOption(
					"(RequestMethod)", method);
		}
	}

}
