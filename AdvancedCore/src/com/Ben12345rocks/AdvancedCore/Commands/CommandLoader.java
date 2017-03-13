package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Commands.GUI.AdminGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.RewardEditGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.UserGUI;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Report.Report;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
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

	/** The instance. */
	static CommandLoader instance = new CommandLoader();

	/**
	 * Gets the single instance of CommandLoader.
	 *
	 * @return single instance of CommandLoader
	 */
	public static CommandLoader getInstance() {
		return instance;
	}

	private ArrayList<String> perms = new ArrayList<String>();

	/**
	 * Instantiates a new command loader.
	 */
	private CommandLoader() {
	}

	public void addPermission(String perm) {
		if (!perms.contains(perm)) {
			perms.add(perm);
		}
	}

	public ArrayList<CommandHandler> getBasicAdminCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();
		cmds.add(new CommandHandler(new String[] { "GiveReward", "(Reward)", "(Player)" }, permPrefix + ".GiveReward",
				"Give a player a reward file", true) {

			@SuppressWarnings("deprecation")
			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[2]);
				RewardHandler.getInstance().giveReward(user, args[1], user.isOnline());

				sender.sendMessage("Gave " + args[2] + " the reward file " + args[1]);
			}
		});
		cmds.add(new CommandHandler(new String[] { "Report" }, permPrefix + ".Report", "Create Report File") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Report.getInstance().create();
				sender.sendMessage("Created zip file");
			}
		});

		cmds.add(new CommandHandler(new String[] { "GUI" }, permPrefix + ".AdminGUI", "Open AdminGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdminGUI.getInstance().openGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Rewards" }, permPrefix + ".RewardEdit", "Open RewardGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				RewardEditGUI.getInstance().openRewardsGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Users" }, permPrefix + ".UserEdit", "Open UserGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUsersGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Users", "(Player)" }, permPrefix + ".UserEdit", "Open UserGUI",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUserGUI((Player) sender, args[1]);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Rewards", "(Reward)" }, permPrefix + ".RewardEdit",
				"Open GUI Reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				RewardEditGUI.getInstance().openRewardGUI((Player) sender, args[1]);
			}
		});
		
		return cmds;
	}

	public ArrayList<CommandHandler> getBasicCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();

		cmds.add(new CommandHandler(new String[] { "SelectChoiceReward" }, permPrefix + ".SelectChoiceReward",
				"Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				new ValueRequest().requestString((Player) sender, "", ArrayUtils.getInstance().convert(

						UserManager.getInstance().getUser(sender.getName()).getChoiceRewards()), true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								player.performCommand("advancedcore selectchoicereward " + value);
							}
						});
			}
		});

		cmds.add(new CommandHandler(new String[] { "SelectChoiceReward", "(Reward)" },
				permPrefix + ".SelectChoiceReward", "Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = RewardHandler.getInstance().getReward(args[1]);
				User user = UserManager.getInstance().getUser((Player) sender);
				if (user.getChoiceRewards().contains(reward.name)) {
					new ValueRequest(InputMethod.INVENTORY).requestString((Player) sender, "",
							ArrayUtils.getInstance().convert(reward.getChoiceRewardsRewards()), false,
							new StringListener() {

								@SuppressWarnings("deprecation")
								@Override
								public void onInput(Player player, String value) {
									User user = UserManager.getInstance().getUser(player);
									RewardHandler.getInstance().giveReward(user, value, true);
									ArrayList<String> rewards = user.getChoiceRewards();
									rewards.remove(value);
									user.setChoiceRewards(rewards);
								}
							});
				} else {
					sender.sendMessage("No rewards to choose");
				}
			}
		});

		cmds.add(new CommandHandler(new String[] { "SetRequestMethod", "(RequestMethod)" },
				permPrefix + ".SetRequestMethod", "SetRequestMethod", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {

				User user = UserManager.getInstance().getUser((Player) sender);
				InputMethod method = InputMethod.valueOf(args[1]);
				if (method == null) {
					user.sendMessage("&cInvalid request method: " + args[1]);
				} else {
					user.setUserInputMethod(method);
					user.sendMessage("&cRequest method set to " + method.toString());
				}

			}
		});

		cmds.add(new CommandHandler(new String[] { "SetRequestMethod" }, permPrefix + ".SetRequestMethod",
				"SetRequestMethod", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ArrayList<String> methods = new ArrayList<String>();
				for (InputMethod method : InputMethod.values()) {
					methods.add(method.toString());
				}
				new ValueRequest(InputMethod.INVENTORY).requestString((Player) sender, "",
						ArrayUtils.getInstance().convert(methods), false, new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								User user = UserManager.getInstance().getUser(player);
								user.setUserInputMethod(InputMethod.valueOf(value));

							}
						});

			}
		});

		return cmds;
	}

	public ArrayList<CommandHandler> getValueReqestCommands() {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();
		cmds.add(new CommandHandler(new String[] { "String", "(String)" }, "", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					StringListener listener = (StringListener) PlayerUtils.getInstance().getPlayerMeta(player,
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

		cmds.add(new CommandHandler(new String[] { "Number", "(Number)" }, "", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					NumberListener listener = (NumberListener) PlayerUtils.getInstance().getPlayerMeta(player,
							"ValueRequestNumber");
					if (args[1].equals("CustomValue")) {
						new ValueRequest().requestNumber(player, listener);
					} else {
						Number number = Double.valueOf(args[1]);
						listener.onInput(player, number);
					}
				} catch (Exception ex) {
					player.sendMessage("No where to input value or error occured");
				}
			}
		});

		cmds.add(new CommandHandler(new String[] { "Boolean", "(Boolean)" }, "", "Command to Input value", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					BooleanListener listener = (BooleanListener) PlayerUtils.getInstance().getPlayerMeta(player,
							"ValueRequestBoolean");
					listener.onInput(player, Boolean.valueOf(args[1]));
				} catch (Exception ex) {
					player.sendMessage("No where to input value");
				}
			}
		});
		return cmds;
	}

	/**
	 * Load commands.
	 */
	public void loadCommands() {

	}

}
