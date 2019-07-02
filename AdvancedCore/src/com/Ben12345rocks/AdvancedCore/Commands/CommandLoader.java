package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Backups.ZipCreator;
import com.Ben12345rocks.AdvancedCore.CommandAPI.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.AdminGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.ChoiceGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.RewardEditGUI;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.UserGUI;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardBuilder;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardOptions;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeChecker;
import com.Ben12345rocks.AdvancedCore.TimeChecker.TimeType;
import com.Ben12345rocks.AdvancedCore.UserManager.UUID;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptEngine;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.Updater.UpdateDownloader;
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

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[2]);
				RewardHandler.getInstance().giveReward(user, args[1], new RewardOptions().setOnline(user.isOnline()));

				sender.sendMessage("Gave " + args[2] + " the reward file " + args[1]);
			}
		});
		cmds.add(new CommandHandler(new String[] { "GiveReward", "(Reward)", "(Player)", "(Text)", "(Text)" },
				permPrefix + ".GiveReward", "Give a player a reward file and set a placeholder", true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[2]);
				RewardHandler.getInstance().giveReward(user, args[1],
						new RewardOptions().setOnline(user.isOnline()).addPlaceholder(args[3], args[4]));

				sender.sendMessage("Gave " + args[2] + " the reward file " + args[1]);
			}
		});
		cmds.add(new CommandHandler(new String[] { "Report" }, permPrefix + ".Report", "Create Report File") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ZipCreator.getInstance().createReport();
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

		cmds.add(new CommandHandler(new String[] { "UserEditValue", "(player)", "(string)", "(string)" },
				permPrefix + ".UserEditValue", "Open UserGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[1]);
				user.getData().setString(args[2], args[3]);
				sendMessage(sender, "&cSet " + args[2] + " to " + args[3] + " for " + args[1]);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Report" }, permPrefix + ".Report",
				"Create a zip file to send for debuging") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ZipCreator.getInstance().createReport();
				sender.sendMessage("Created Zip File!");
			}
		});

		cmds.add(
				new CommandHandler(new String[] { "UserRemove", "(uuid)" }, permPrefix + ".UserRemove", "Remove User") {

					@Override
					public void execute(CommandSender sender, String[] args) {
						sendMessage(sender, "&cRemoving " + args[1]);
						User user = UserManager.getInstance().getUser(new UUID(args[1]));
						user.getData().remove();
						sendMessage(sender, "&cRemoved " + args[1]);
					}
				});

		cmds.add(new CommandHandler(new String[] { "ClearCache" }, permPrefix + ".ClearCache", "Clear MySQL Cache") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
					if (AdvancedCorePlugin.getInstance().getMysql() != null) {
						AdvancedCorePlugin.getInstance().getMysql().clearCache();
						sender.sendMessage(StringUtils.getInstance().colorize("&cCache cleared"));
					} else {
						sender.sendMessage(StringUtils.getInstance().colorize("&cMySQL not loaded"));
					}
				} else {
					sender.sendMessage(StringUtils.getInstance().colorize("&cCurrent storage type not mysql"));
				}
			}
		});

		cmds.add(new CommandHandler(new String[] { "Purge" }, permPrefix + ".Purge", "Purge Data") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserManager.getInstance().purgeOldPlayers();
				sendMessage(sender, "&cPurged data");
			}
		});

		if (AdvancedCorePlugin.getInstance().getOptions().getResourceId() != 0) {
			cmds.add(new CommandHandler(new String[] { "Download" }, permPrefix + ".Download", "Download from spigot") {

				@Override
				public void execute(CommandSender sender, String[] args) {
					sender.sendMessage(StringUtils.getInstance().colorize(
							"&cAttempting to download... restart server to fully update, Note: Jar may not be latest version (40 min or so update delay)"));
					if (UpdateDownloader.getInstance().download(AdvancedCorePlugin.getInstance(),
							AdvancedCorePlugin.getInstance().getOptions().getResourceId())) {
						sender.sendMessage(StringUtils.getInstance().colorize("&cDownloaded jar."));
					} else {
						sendMessage(sender, "&cFailed to download jar.");
					}
				}
			});
		}

		if (!AdvancedCorePlugin.getInstance().getJenkinsSite().isEmpty()) {
			cmds.add(new CommandHandler(new String[] { "DownloadJenkins" }, permPrefix + ".Download",
					"Download from jenkins. Please use at your own risk") {

				@Override
				public void execute(CommandSender sender, String[] args) {
					if (AdvancedCorePlugin.getInstance().getOptions().isEnableJenkins()) {
						sender.sendMessage(StringUtils.getInstance().colorize(
								"&cAttempting to download from jenkins... restart server to fully update, Note: USE THESE DEV BUILDS AT YOUR OWN RISK"));
						UpdateDownloader.getInstance().downloadFromJenkins(
								AdvancedCorePlugin.getInstance().getJenkinsSite(),
								AdvancedCorePlugin.getInstance().getName());
						sender.sendMessage(StringUtils.getInstance().colorize("&cDownloaded jar."));
					} else {
						sendMessage(sender,
								"&cNot enabled, please enable to use this. Note: USE THESE DEV BUILDS AT YOUR OWN RISK");
					}
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

		cmds.add(new CommandHandler(new String[] { "Javascript", "(List)" }, permPrefix + ".Javascript",
				"Execute javascript") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String str = "";
				for (int i = 1; i < args.length; i++) {
					str += args[i] + " ";
				}
				JavascriptEngine engine = new JavascriptEngine();
				engine.addPlayer(sender);
				sendMessage(sender, "&cJavascript result: " + engine.getStringValue(str.trim()));
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

		cmds.add(new CommandHandler(new String[] { "User", "(player)", "SetData", "(text)", "(text)" },
				permPrefix + ".SetData", "Set user data") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[1]);
				user.getData().setString(args[3], args[4]);
				sender.sendMessage(
						StringUtils.getInstance().colorize("&cSet " + args[3] + " for " + args[1] + " to " + args[4]));
			}
		});

		cmds.add(new CommandHandler(new String[] { "User", "(Player)", "ViewData" }, permPrefix + ".ViewData",
				"View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[1]);
				for (String key : user.getData().getKeys()) {
					user.sendMessage("&c&l" + key + " &c" + user.getData().getString(key));
				}
			}
		});

		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}

		return cmds;
	}

	public ArrayList<CommandHandler> getBasicCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();

		cmds.add(new CommandHandler(new String[] { "Choices" }, permPrefix + ".Choices",
				"Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ChoiceGUI.getInstance().openClaimChoices((Player) sender);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Choices", "SetPreference", "(ChoiceReward)" },
				permPrefix + ".ChoicesPreference", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ChoiceGUI.getInstance().openPreferenceReward((Player) sender, args[2]);
			}
		});

		cmds.add(new CommandHandler(new String[] { "Choices", "SetPreference", "(ChoiceReward)", "(String)" },
				permPrefix + ".ChoicesPreference", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser((Player) sender);
				user.setChoicePreference(args[2], args[3]);

				user.sendMessage(AdvancedCorePlugin.getInstance().getOptions().getFormatChoiceRewardsPreferenceSet(),
						"choice", args[3]);
			}
		});

		cmds.add(new CommandHandler(
				new String[] { "Choices", "SetPreference", "(ChoiceReward)", "(String)", "(Player)" },
				permPrefix + ".ChoicesSetPreferenceOther", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				User user = UserManager.getInstance().getUser(args[4]);
				user.setChoicePreference(args[2], args[3]);

				user.sendMessage("&cPreference set to " + args[3] + " for " + args[4]);
			}
		});

		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}

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
		}.ignoreNumberCheck());

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
		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}
		return cmds;
	}
}
