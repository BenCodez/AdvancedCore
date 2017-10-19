package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.AdminGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.RewardEditGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.UserGUI;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.RewardBuilder;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Objects.TabCompleteHandle;
import com.Ben12345rocks.AdvancedCore.Objects.TabCompleteHandler;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.Report.Report;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeType;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.Updater.SpigetUpdater;
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
		cmds.add(new CommandHandler(new String[] { "GiveAll", "(reward)" }, permPrefix + ".GiveAll",
				"Give all users a reward") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = RewardHandler.getInstance().getReward(args[1]);
				ArrayList<User> users = new ArrayList<User>();
				for (String uuid : UserManager.getInstance().getAllUUIDs()) {
					User user = UserManager.getInstance().getUser(new UUID(uuid));
					users.add(user);
				}
				for (User user : users) {
					new RewardBuilder(reward).send(user);
				}
			}
		});

		cmds.add(new CommandHandler(new String[] { "GiveAllOnline", "(reward)" }, permPrefix + ".GiveAllOnline",
				"Give all users a reward") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = RewardHandler.getInstance().getReward(args[1]);
				for (Player p : Bukkit.getOnlinePlayers()) {
					User user = UserManager.getInstance().getUser(p);
					new RewardBuilder(reward).send(user);
				}
			}
		});
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

		cmds.add(new CommandHandler(new String[] { "ClearCache" }, permPrefix + ".ClearCache", "Clear MySQL Cache") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
					if (AdvancedCoreHook.getInstance().getMysql() != null) {
						AdvancedCoreHook.getInstance().getMysql().clearCache();
						sender.sendMessage(StringUtils.getInstance().colorize("&cCache cleared"));
					} else {
						sender.sendMessage(StringUtils.getInstance().colorize("&cMySQL not loaded"));
					}
				} else {
					sender.sendMessage(StringUtils.getInstance().colorize("&cCurrent storage type not mysql"));
				}
			}
		});

		if (AdvancedCoreHook.getInstance().getResourceId() != 0) {
			cmds.add(new CommandHandler(new String[] { "Download" }, permPrefix + ".Download", "Download from spigot") {

				@Override
				public void execute(CommandSender sender, String[] args) {
					sender.sendMessage(StringUtils.getInstance().colorize(
							"&cAttempting to download... restart server to fully update, Note: Jar may not be latest version (40 min or so update delay)"));
					SpigetUpdater.getInstance().download(AdvancedCoreHook.getInstance().getPlugin(),
							AdvancedCoreHook.getInstance().getResourceId());
					sender.sendMessage(StringUtils.getInstance().colorize("&cDownloaded jar."));
				}
			});
		}

		cmds.add(new CommandHandler(new String[] { "ForceTimeChanged", "(TimeType)" }, permPrefix + ".ForceTimeChange",
				"Force time change, use at your own risk!") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				try {
					TimeType time = TimeType.getTimeType(args[1]);
					sender.sendMessage("Forcing change for " + time.toString());
					TimeChecker.getInstance().forceChanged(time);
					sender.sendMessage("Forced change for " + time.toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
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
				new ValueRequest(InputMethod.INVENTORY).requestString((Player) sender, "",
						ArrayUtils.getInstance().convert(

								UserManager.getInstance().getUser(sender.getName()).getChoiceRewards()),
						true, new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								selectChoiceReward(sender, value);
							}
						});
			}
		});

		cmds.add(new CommandHandler(new String[] { "SelectChoiceReward", "(Reward)" },
				permPrefix + ".SelectChoiceReward", "Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				selectChoiceReward(sender, args[1]);
			}
		});

		cmds.add(new CommandHandler(new String[] { "SetRequestMethod", "(RequestMethod)" },
				permPrefix + ".SetRequestMethod", "SetRequestMethod", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {

				User user = UserManager.getInstance().getUser((Player) sender);
				InputMethod method = InputMethod.getMethod(args[1]);
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
								user.setUserInputMethod(InputMethod.getMethod(value));

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

	public void selectChoiceReward(CommandSender sender, final String rewardSt) {
		Reward reward = RewardHandler.getInstance().getReward(rewardSt);
		User user = UserManager.getInstance().getUser((Player) sender);
		if (user.getChoiceRewards().contains(reward.getName())) {
			new ValueRequest(InputMethod.INVENTORY).requestString((Player) sender, "",
					ArrayUtils.getInstance().convert(reward.getChoiceRewardsRewards()), false, new StringListener() {

						@SuppressWarnings("deprecation")
						@Override
						public void onInput(Player player, String value) {
							User user = UserManager.getInstance().getUser(player);
							RewardHandler.getInstance().giveReward(user, value, true);
							ArrayList<String> rewards = user.getChoiceRewards();
							rewards.remove(rewardSt);
							user.setChoiceRewards(rewards);
						}
					});
		} else {
			sender.sendMessage("No rewards to choose");
		}
	}

	public void loadTabComplete() {
		TabCompleteHandler.getInstance()
				.addTabCompleteOption(new TabCompleteHandle("(Player)", new ArrayList<String>()) {

					@Override
					public void updateReplacements() {
						for (Player player : Bukkit.getOnlinePlayers()) {
							if (!getReplace().contains(player.getName())) {
								getReplace().add(player.getName());
							}
						}

					}

					@Override
					public void reload() {
						ArrayList<String> players = new ArrayList<String>();
						for (String name : AdvancedCoreHook.getInstance().getUuids().keySet()) {
							if (!players.contains(name)) {
								players.add(name);
							}
						}
						setReplace(players);
					}
				});

		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(uuid)", new ArrayList<String>()) {

			@Override
			public void updateReplacements() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (!getReplace().contains(player.getUniqueId().toString())) {
						getReplace().add(player.getUniqueId().toString());
					}
				}
			}

			@Override
			public void reload() {
				ArrayList<String> uuids = new ArrayList<String>();
				for (String name : AdvancedCoreHook.getInstance().getUuids().values()) {
					if (!uuids.contains(name)) {
						uuids.add(name);
					}
				}
				setReplace(uuids);
			}
		});

		ArrayList<String> options = new ArrayList<String>();
		options.add("True");
		options.add("False");
		TabCompleteHandler.getInstance().addTabCompleteOption("(Boolean)", options);
		options = new ArrayList<String>();
		TabCompleteHandler.getInstance().addTabCompleteOption("(List)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(String)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption(new TabCompleteHandle("(Reward)", options) {

			@Override
			public void updateReplacements() {

			}

			@Override
			public void reload() {
				ArrayList<String> rewards = new ArrayList<String>();
				for (Reward reward : RewardHandler.getInstance().getRewards()) {
					rewards.add(reward.getRewardName());
				}
				setReplace(rewards);
			}
		});

		ArrayList<String> method = new ArrayList<String>();
		for (InputMethod me : InputMethod.values()) {
			method.add(me.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(RequestMethod)", method);

		ArrayList<String> userStorage = new ArrayList<String>();
		for (UserStorage storage : UserStorage.values()) {
			userStorage.add(storage.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(UserStorage)", userStorage);

		ArrayList<String> times = new ArrayList<String>();
		for (TimeType ty : TimeType.values()) {
			times.add(ty.toString());
		}
		TabCompleteHandler.getInstance().addTabCompleteOption("(Number)", options);
		TabCompleteHandler.getInstance().addTabCompleteOption("(TimeType)", times);
	}
}
