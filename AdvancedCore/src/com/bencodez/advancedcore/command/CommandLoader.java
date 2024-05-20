package com.bencodez.advancedcore.command;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.backup.ZipCreator;
import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.updater.UpdateDownloader;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.listeners.BooleanListener;
import com.bencodez.advancedcore.api.valuerequest.listeners.NumberListener;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;
import com.bencodez.advancedcore.command.gui.AdminGUI;
import com.bencodez.advancedcore.command.gui.ChoiceGUI;
import com.bencodez.advancedcore.command.gui.RewardEditGUI;
import com.bencodez.advancedcore.command.gui.UserGUI;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.player.PlayerUtils;

import lombok.Getter;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandLoader.
 */
public class CommandLoader {

	static CommandLoader instance = new CommandLoader(AdvancedCorePlugin.getInstance());

	public static CommandLoader getInstance() {
		return instance;
	}

	private ArrayList<String> perms = new ArrayList<String>();

	private AdvancedCorePlugin plugin;

	@Getter
	ArrayList<CommandHandler> valueRequestCommands = new ArrayList<CommandHandler>();

	public CommandLoader(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	public void addPermission(String perm) {
		if (!perms.contains(perm)) {
			perms.add(perm);
		}
	}

	public ArrayList<CommandHandler> getBasicAdminCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();
		cmds.add(new CommandHandler(plugin, new String[] { "RunCMD", "All", "(List)" }, permPrefix + ".RunCMD.All",
				"Run command for every user, use %player% for player") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String str = "";
				for (int i = 2; i < args.length; i++) {
					str += args[i] + " ";
				}
				final String cmd = str;
				ArrayList<AdvancedCoreUser> users = new ArrayList<AdvancedCoreUser>();
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
					user.dontCache();
					users.add(user);

					plugin.getBukkitScheduler().runTask(plugin, new Runnable() {

						@Override
						public void run() {
							Bukkit.getServer().dispatchCommand(sender,
									PlaceholderUtils.replacePlaceHolder(cmd, "player", user.getPlayerName()));
						}
					});

				}
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "RunSQLQuery", "(List)" }, permPrefix + ".RunSQLQuery",
				"Execute sql query", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String str = "";
				for (int i = 1; i < args.length; i++) {
					if (i + 1 == args.length) {
						str += args[i] + ";";
					} else {
						str += args[i] + " ";
					}
				}

				switch (plugin.getStorageType()) {
				case MYSQL:
					sendMessage(sender, "Running query: " + str);
					plugin.getMysql().executeQuery(str);
					sendMessage(sender, "Query finished: " + str);
					break;
				case SQLITE:
					sendMessage(sender, "Running query: " + str);
					plugin.getSQLiteUserTable().executeQuery(str);
					sendMessage(sender, "Query finished: " + str);
					break;
				default:
					break;
				}

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "UpdateMySQLColumnSizes" },
				permPrefix + ".UpdateMySQLColumn", "Update current mysql column sizes", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (plugin.getOptions().getStorageType().equals(UserStorage.MYSQL)) {
					for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
						plugin.getMysql().alterColumnType(key.getKey(), key.getColumnType());
					}
					sendMessage(sender, "&cColumn sizes updated");
				} else {
					sendMessage(sender, "&cNot using MySQL");
				}

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "TotalNumberOfUsers" }, permPrefix + ".TotalNumberOfUsers",
				"Gets current number of users in VotingPlugin database", true, false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "Total number of users: " + plugin.getUserManager().getAllUUIDs().size());

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "GiveAll", "(reward)" }, permPrefix + ".GiveAll",
				"Give all users a reward") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = plugin.getRewardHandler().getReward(args[1]);
				ArrayList<AdvancedCoreUser> users = new ArrayList<AdvancedCoreUser>();
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
					user.dontCache();
					users.add(user);
				}
				for (AdvancedCoreUser user : users) {
					new RewardBuilder(reward).send(user);
				}
				sendMessage(sender, "&cGave all players reward file " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "GiveAllOnline", "(reward)" }, permPrefix + ".GiveAllOnline",
				"Give all users a reward") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = plugin.getRewardHandler().getReward(args[1]);
				for (Player p : Bukkit.getOnlinePlayers()) {
					AdvancedCoreUser user = plugin.getUserManager().getUser(p);
					new RewardBuilder(reward).send(user);
				}
			}
		});
		cmds.add(new CommandHandler(plugin, new String[] { "GiveReward", "(Player)", "(Reward)" },
				permPrefix + ".GiveReward", "Give a player a reward file", true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				plugin.getRewardHandler().giveReward(user, args[2], new RewardOptions().setOnline(user.isOnline()));

				sendMessage(sender, "&cGave " + args[1] + " the reward file " + args[2]);
			}
		});

		cmds.add(new PlayerCommandHandler(plugin, new String[] { "User", "(Player)", "ForceReward", "(Reward)" },
				permPrefix + ".GiveReward", "Give a player a reward file", true) {

			@Override
			public void executeSinglePlayer(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				plugin.getRewardHandler().giveReward(user, args[3], new RewardOptions().setOnline(user.isOnline()));

				sender.sendMessage("&cGave " + args[1] + " the reward file " + args[3]);
			}

			@Override
			public void executeAll(CommandSender sender, String[] args) {
				Reward reward = plugin.getRewardHandler().getReward(args[3]);
				ArrayList<AdvancedCoreUser> users = new ArrayList<AdvancedCoreUser>();
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
					user.dontCache();
					users.add(user);
				}
				for (AdvancedCoreUser user : users) {
					new RewardBuilder(reward).send(user);
				}
				sendMessage(sender, "&cGave all players reward file " + args[3]);
			}
		});
		cmds.add(new CommandHandler(plugin, new String[] { "GiveReward", "(Player)", "(Reward)", "(Text)", "(Text)" },
				permPrefix + ".GiveReward", "Give a player a reward file and set a placeholder", true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				plugin.getRewardHandler().giveReward(user, args[2],
						new RewardOptions().setOnline(user.isOnline()).addPlaceholder(args[3], args[4]));

				sender.sendMessage("&cGave " + args[1] + " the reward file " + args[2]);
			}
		});
		cmds.add(new CommandHandler(plugin, new String[] { "Report" }, permPrefix + ".Report", "Create Report File") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ZipCreator.getInstance().createReport();
				sender.sendMessage("Created zip file");
			}
		});
		cmds.add(new CommandHandler(plugin, new String[] { "ClearOfflineRewards" }, permPrefix + ".ClearOfflineRewards",
				"Clear offline rewards", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cStarting to clear offline rewards");
				plugin.getUserManager().removeAllKeyValues(plugin.getUserManager().getOfflineRewardsPath(),
						DataType.STRING);
				sendMessage(sender, "&cFinished clearing offline rewards");
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "GUI" }, permPrefix + ".AdminGUI", "Open AdminGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdminGUI.getInstance().openGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Rewards" }, permPrefix + ".RewardEdit", "Open RewardGUI",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				RewardEditGUI.getInstance().openRewardsGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User" }, permPrefix + ".UserEdit", "Open UserGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUsersGUI((Player) sender);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)" }, permPrefix + ".UserEdit",
				"Open UserGUI", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				UserGUI.getInstance().openUserGUI((Player) sender, args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "RemoveTempPermissions" },
				permPrefix + ".RemoveTempPermission", "Remove temp permissions") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				plugin.getPermissionHandler()
						.removePermission(UUID.fromString(PlayerManager.getInstance().getUUID(args[1])));
				sendMessage(sender, "&cRemoved temporary permissions from " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin,
				new String[] { "User", "(Player)", "AddTempPermissions", "(Text)", "(Number)" },
				permPrefix + ".AddTempPermission", "Add temp permission for number of seconds") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				plugin.getPermissionHandler().addPermission(
						UUID.fromString(PlayerManager.getInstance().getUUID(args[1])), args[3],
						Integer.valueOf(args[4]));
				sendMessage(sender, "&cAdded temporary permission to " + args[1] + " for " + args[4]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "AddTempPermissions", "(Text)" },
				permPrefix + ".AddTempPermission", "Add temp permission") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				plugin.getPermissionHandler()
						.addPermission(UUID.fromString(PlayerManager.getInstance().getUUID(args[1])), args[3]);
				sendMessage(sender, "&cAdded temporary permission to " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Report" }, permPrefix + ".Report",
				"Create a zip file to send for debuging") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ZipCreator.getInstance().createReport();
				sender.sendMessage("Created Zip File!");
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "UserRemove", "(player)" }, permPrefix + ".UserRemove",
				"Remove User") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cRemoving " + args[1]);
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				user.getData().remove();
				sendMessage(sender, "&cRemoved " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "UserUUIDRemove", "(uuid)" }, permPrefix + ".UserRemove",
				"Remove User") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cRemoving " + args[1]);
				AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(args[1]));
				user.getData().remove();
				sendMessage(sender, "&cRemoved " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "ClearCache" }, permPrefix + ".ClearCache",
				"Clear MySQL Cache") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
					if (plugin.getMysql() != null) {
						plugin.getMysql().clearCacheBasic();
					} else {
						sender.sendMessage(MessageAPI.colorize("&cMySQL not loaded"));
					}
				}

				plugin.getUserManager().getDataManager().clearCache();

				sender.sendMessage(MessageAPI.colorize("&cCache cleared"));

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Purge" }, permPrefix + ".Purge", "Purge Data") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				plugin.getUserManager().purgeOldPlayersNow();
				sendMessage(sender, "&cPurged data");
			}
		});

		if (!plugin.getJenkinsSite().isEmpty()) {
			cmds.add(new CommandHandler(plugin, new String[] { "DownloadJenkins" }, permPrefix + ".Download",
					"Download from jenkins. Please use at your own risk") {

				@Override
				public void execute(CommandSender sender, String[] args) {
					if (plugin.getOptions().isEnableJenkins() && !plugin.getJenkinsSite().isEmpty()) {
						sender.sendMessage(MessageAPI.colorize(
								"&cAttempting to download from jenkins... restart server to fully update, Note: USE THESE DEV BUILDS AT YOUR OWN RISK"));
						UpdateDownloader.getInstance().downloadFromJenkins(plugin.getJenkinsSite(), plugin.getName());
						sender.sendMessage(MessageAPI.colorize("&cDownloaded jar"));
					} else {
						sendMessage(sender,
								"&cNot enabled, please enable to use this. Note: USE THESE DEV BUILDS AT YOUR OWN RISK");
					}
				}
			});
		}

		cmds.add(new CommandHandler(plugin, new String[] { "ForceTimeChange", "(TimeType)" },
				permPrefix + ".ForceTimeChange", "Force time change, use at your own risk!", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				try {
					TimeType time = TimeType.getTimeType(args[1]);
					sendMessage(sender,
							"&cForcing time change for " + time.toString() + ". May take awhile to process");
					plugin.getTimeChecker().forceChanged(time);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Javascript", "(List)" }, permPrefix + ".Javascript",
				"Execute javascript") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String str = "";
				for (int i = 1; i < args.length; i++) {
					str += args[i] + " ";
				}
				if (sender instanceof Player) {
					str = PlaceholderUtils.replacePlaceHolders((Player) sender, str);
				}
				JavascriptEngine engine = new JavascriptEngine();
				engine.addPlayer(sender);
				sendMessage(sender, "&cJavascript result: " + engine.getStringValue(str.trim()));
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "SetRequestMethod", "(RequestMethod)" },
				permPrefix + ".SetRequestMethod", "SetRequestMethod", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {

				AdvancedCoreUser user = plugin.getUserManager().getUser((Player) sender);
				InputMethod method = InputMethod.getMethod(args[1]);
				if (method == null) {
					user.sendMessage("&cInvalid request method: " + args[1]);
				} else {
					user.setUserInputMethod(method);
					user.sendMessage("&cRequest method set to " + method.toString());
				}

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "SetRequestMethod" }, permPrefix + ".SetRequestMethod",
				"SetRequestMethod", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ArrayList<String> methods = new ArrayList<String>();
				for (InputMethod method : InputMethod.values()) {
					methods.add(method.toString());
				}
				new ValueRequest(InputMethod.INVENTORY).requestString((Player) sender, "",
						ArrayUtils.convert(methods), false, new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								AdvancedCoreUser user = plugin.getUserManager().getUser(player);
								user.setUserInputMethod(InputMethod.getMethod(value));

							}
						});

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "All", "SetData", "(text)", "(text)" },
				permPrefix + ".SetAllData", "Set all users data") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String data = args[4];
				if (data.equalsIgnoreCase("\"\"")) {
					data = "";
				}
				for (String uuid : plugin.getUserManager().getAllUUIDs()) {
					AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
					user.dontCache();
					user.getData().setString(args[3], data);

				}
				sender.sendMessage(MessageAPI.colorize("&cSet all users " + args[3] + " to " + args[4]));
			}
		});

		cmds.add(new PlayerCommandHandler(plugin, new String[] { "User", "(player)", "SetData", "(text)", "(text)" },
				permPrefix + ".SetData", "Set user data") {

			@Override
			public void executeSinglePlayer(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				String data = args[4];
				if (data.equalsIgnoreCase("\"\"")) {
					data = "";
				}
				user.getData().setString(args[3], data);
				sender.sendMessage(MessageAPI.colorize("&cSet " + args[3] + " for " + args[1] + " to " + args[4]));
			}

			@Override
			public void executeAll(CommandSender sender, String[] args) {
				if (sender.hasPermission(permPrefix + ".SetAllData")) {
					String data = args[4];
					if (data.equalsIgnoreCase("\"\"")) {
						data = "";
					}
					for (String uuid : plugin.getUserManager().getAllUUIDs()) {
						AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(uuid));
						user.dontCache();
						user.getData().setString(args[3], data);

					}
					sender.sendMessage(MessageAPI.colorize("&cSet all users " + args[3] + " to " + args[4]));
				}
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ViewData" }, permPrefix + ".ViewData",
				"View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				for (Entry<String, DataValue> entry : user.getData().getValues().entrySet()) {
					sendMessage(sender, "&c&l" + entry.getKey() + " &c" + entry.getValue().toString());
				}
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ViewCache" }, permPrefix + ".ViewCache",
				"View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				for (String str : user.getCache().displayCacheStringList()) {
					sender.sendMessage(str);
				}

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ForceCache" },
				permPrefix + ".ForceCache", "View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				user.cache();
				sendMessage(sender, "&aForced cached " + args[1]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "HasPermission", "(Text)" },
				permPrefix + ".HasPermission", "View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[1]);
				sendMessage(sender, "User " + args[1] + " permission " + args[3] + ":" + user.hasPermission(args[3]));
			}
		});

		cmds.add(new CommandHandler(plugin,
				new String[] { "Choices", "SetPreference", "(ChoiceReward)", "(String)", "(Player)" },
				permPrefix + ".ChoicesSetPreferenceOther", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(args[4]);
				user.setChoicePreference(args[2], args[3]);

				user.sendMessage("&cPreference set to " + args[3] + " for " + args[4]);
			}
		});

		if (plugin.isLoadUserData()) {
			cmds.add(new CommandHandler(plugin, new String[] { "ConvertToData", "(UserStorage)" },
					permPrefix + ".Commands.AdminVote.ConvertToData",
					"Convert user storage from current storage type the one specificed", true, true) {

				@Override
				public void execute(CommandSender sender, String[] args) {
					sendMessage(sender,
							"&cStarting convert from " + plugin.getStorageType().toString() + " to " + args[1]);
					plugin.convertDataStorage(plugin.getStorageType(), UserStorage.value(args[1]));
					sendMessage(sender, "&cFinished converting");
				}
			});

			cmds.add(new CommandHandler(plugin, new String[] { "ConvertFromData", "(UserStorage)" },
					permPrefix + ".Commands.AdminVote.ConvertFromData",
					"Convert user storage from current storage type from the one specificed", true, true) {

				@Override
				public void execute(CommandSender sender, String[] args) {
					sendMessage(sender,
							"&cStarting convert from " + args[1] + " to " + plugin.getStorageType().toString());
					plugin.convertDataStorage(UserStorage.value(args[1]), plugin.getStorageType());
					sendMessage(sender, "&cFinished converting");
				}
			});
		}

		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}

		return cmds;
	}

	public ArrayList<CommandHandler> getBasicCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();

		cmds.add(new CommandHandler(plugin, new String[] { "Choices" }, permPrefix + ".Choices",
				"Let user select his choice reward", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ChoiceGUI.getInstance().openClaimChoices((Player) sender);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Choices", "SetPreference", "(ChoiceReward)" },
				permPrefix + ".ChoicesPreference", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				ChoiceGUI.getInstance().openPreferenceReward((Player) sender, args[2]);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "Choices", "SetPreference", "(ChoiceReward)", "(String)" },
				permPrefix + ".ChoicesPreference", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				AdvancedCoreUser user = plugin.getUserManager().getUser((Player) sender);
				user.setChoicePreference(args[2], args[3]);

				user.sendMessage(plugin.getOptions().getFormatChoiceRewardsPreferenceSet(), "choice", args[3]);
			}
		});

		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}

		return cmds;
	}

	public void loadValueRequestCommands() {
		ArrayList<CommandHandler> cmds = new ArrayList<CommandHandler>();
		cmds.add(
				new CommandHandler(plugin, new String[] { "String", "(String)" }, "", "Command to Input value", false) {

					@Override
					public void execute(CommandSender sender, String[] args) {
						Player player = (Player) sender;
						try {
							StringListener listener = (StringListener) PlayerUtils.getPlayerMeta(plugin, player,
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

		cmds.add(
				new CommandHandler(plugin, new String[] { "Number", "(Number)" }, "", "Command to Input value", false) {

					@Override
					public void execute(CommandSender sender, String[] args) {
						Player player = (Player) sender;
						try {
							NumberListener listener = (NumberListener) PlayerUtils.getPlayerMeta(plugin, player,
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

		cmds.add(new CommandHandler(plugin, new String[] { "Boolean", "(Boolean)" }, "", "Command to Input value",
				false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				try {
					BooleanListener listener = (BooleanListener) PlayerUtils.getPlayerMeta(plugin, player,
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
		valueRequestCommands = cmds;
	}
}
