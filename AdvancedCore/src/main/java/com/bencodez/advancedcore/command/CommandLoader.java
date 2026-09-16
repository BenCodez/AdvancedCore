package com.bencodez.advancedcore.command;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.backup.ZipCreator;
import com.bencodez.advancedcore.api.command.CommandHandler;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.command.gui.AdminGUI;
import com.bencodez.advancedcore.command.gui.ChoiceGUI;
import com.bencodez.advancedcore.command.gui.RewardEditGUI;
import com.bencodez.advancedcore.command.gui.UserGUI;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.valuerequest.InputMethod;
import com.bencodez.simpleapi.valuerequest.PlayerInputManager;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandLoader.
 */
public class CommandLoader {

	static CommandLoader instance = new CommandLoader(AdvancedCorePlugin.getInstance());

	/**
	 * Gets the instance.
	 *
	 * @return the instance
	 */
	public static CommandLoader getInstance() {
		return instance;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
	}

	private void withResolvedUser(CommandSender sender, String playerName, Consumer<AdvancedCoreUser> action) {
		plugin.getUserManager().getUserAsync(playerName,
				user -> runRecipientCallback(UUID.fromString(user.getUUID()), () -> action.accept(user)),
				failure -> runCommandCallback(sender,
						() -> sender.sendMessage(MessageAPI.colorize("&cUnable to resolve UUID for " + playerName))));
	}

	private ArrayList<String> perms = new ArrayList<>();

	private AdvancedCorePlugin plugin;

	/**
	 * Instantiates a new command loader.
	 *
	 * @param plugin the plugin
	 */
	public CommandLoader(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Adds a permission.
	 *
	 * @param perm the permission
	 */
	public void addPermission(String perm) {
		if (!perms.contains(perm)) {
			perms.add(perm);
		}
	}

	/**
	 * Gets the basic admin commands.
	 *
	 * @param permPrefix the permission prefix
	 * @return the basic admin commands
	 */
	public ArrayList<CommandHandler> getBasicAdminCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<>();

		cmds.add(new CommandHandler(plugin, new String[] { "RunCMD", "All", "(List)" }, permPrefix + ".RunCMD.All",
				"Run command for every user, use %player% for player") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				String str = "";
				for (int i = 2; i < args.length; i++) {
					str += args[i] + " ";
				}
				final String cmd = str;

				// Stream instead of building a huge users list. Shared storage rejects
				// primary-thread reads, so enumeration stays on the worker.
				runUserStorageCommand(sender, () -> plugin.getUserManager().forEachUserKeys((uuid, columns) -> {
					AdvancedCoreUser user = plugin.getUserManager().getUser(uuid, false);
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					user.updateTempCacheWithColumns(columns);
					String playerName = user.getPlayerName();
					plugin.getBukkitScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(sender,
							PlaceholderUtils.replacePlaceHolder(cmd, "player", playerName)));
				}, null), null);
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
				if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
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
				runUserStorageCommand(sender, () -> {
					int count = plugin.getUserManager().getAllUUIDs().size();
					runCommandCallback(sender, () -> sendMessage(sender, "Total number of users: " + count));
				}, null);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "GiveAll", "(reward)" }, permPrefix + ".GiveAll",
				"Give all users a reward") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Reward reward = plugin.getRewardHandler().getReward(args[1]);

				runUserStorageCommand(sender, () -> plugin.getUserManager().forEachUserKeys((uuid, columns) -> {
					AdvancedCoreUser user = plugin.getUserManager().getUser(uuid, false);
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					user.updateTempCacheWithColumns(columns);
					// Reward actions can touch Bukkit APIs, so return each one to the owner thread.
					runRecipientCallback(uuid, () -> new RewardBuilder(reward).send(user));
				}, null), () -> sendMessage(sender, "&cGave all players reward file " + args[1]));
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
				withResolvedUser(sender, args[1], user -> {
					plugin.getRewardHandler().giveReward(user, args[2], new RewardOptions().setOnline(user.isOnline()));
					runCommandCallback(sender,
							() -> sendMessage(sender, "&cGave " + args[1] + " the reward file " + args[2]));
				});
			}
		});

		cmds.add(new PlayerCommandHandler(plugin, new String[] { "User", "(Player)", "ForceReward", "(Reward)" },
				permPrefix + ".GiveReward", "Give a player a reward file", true) {

			@Override
			public void executeAll(CommandSender sender, String[] args) {
				Reward reward = plugin.getRewardHandler().getReward(args[3]);

				runUserStorageCommand(sender, () -> plugin.getUserManager().forEachUserKeys((uuid, columns) -> {
					AdvancedCoreUser user = plugin.getUserManager().getUser(uuid, false);
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					user.updateTempCacheWithColumns(columns);
					runRecipientCallback(uuid, () -> new RewardBuilder(reward).send(user));
				}, null), () -> sendMessage(sender, "&cGave all players reward file " + args[3]));
			}

			@Override
			public void executeSinglePlayer(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					plugin.getRewardHandler().giveReward(user, args[3], new RewardOptions().setOnline(user.isOnline()));
					runCommandCallback(sender, () -> sender.sendMessage("&cGave " + args[1] + " the reward file " + args[3]));
				});
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "GiveReward", "(Player)", "(Reward)", "(Text)", "(Text)" },
				permPrefix + ".GiveReward", "Give a player a reward file and set a placeholder", true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					plugin.getRewardHandler().giveReward(user, args[2],
							new RewardOptions().setOnline(user.isOnline()).addPlaceholder(args[3], args[4]));
					runCommandCallback(sender, () -> sender.sendMessage("&cGave " + args[1] + " the reward file " + args[2]));
				});
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
				runUserStorageCommand(sender,
						() -> plugin.getUserManager().removeAllKeyValues(plugin.getUserManager().getOfflineRewardsPath(),
								DataType.STRING),
						() -> sendMessage(sender, "&cFinished clearing offline rewards"));
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "ForceRunOfflineRewards" },
				permPrefix + ".ForceRunOfflineRewards",
				"Force run all offline rewards as if they were online for all players", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cStarting to run offline rewards");

				runUserStorageCommand(sender, () -> {
					ForcedReplayBarrier barrier = new ForcedReplayBarrier(
							failure -> reportForcedReplayCompletion(sender, null, failure));
					plugin.getUserManager().forEachUserKeys((uuid, columns) -> {
						AdvancedCoreUser user = plugin.getUserManager().getUser(uuid, false);
						user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
						user.updateTempCacheWithColumns(columns);
						barrier.add(user.forceRunOfflineRewardsAsync());
					}, ignored -> barrier.enumerationComplete());
				}, null);
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "ForceRunOfflineRewards", "(player)" },
				permPrefix + ".ForceRunOfflineRewards",
				"Force run all offline rewards as if they were online for a specific player", true, true) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cStarting to run offline rewards for " + args[1]);

				withResolvedUser(sender, args[1], user -> {
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					user.forceRunOfflineRewardsAsync().whenComplete((ignored, failure) ->
							reportForcedReplayCompletion(sender, args[1], failure));
				});
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

		// ===== UUIDNameCache removal: use UuidLookup everywhere =====

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "RemoveTempPermissions" },
				permPrefix + ".RemoveTempPermission", "Remove temp permissions") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					plugin.getPermissionHandler().removePermission(UUID.fromString(user.getUUID()));
					runCommandCallback(sender,
							() -> sendMessage(sender, "&cRemoved temporary permissions from " + args[1]));
				});
			}
		});

		cmds.add(new CommandHandler(plugin,
				new String[] { "User", "(Player)", "AddTempPermissions", "(Text)", "(Number)" },
				permPrefix + ".AddTempPermission", "Add temp permission for number of seconds") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					plugin.getPermissionHandler().addPermission(UUID.fromString(user.getUUID()), args[3],
							Integer.valueOf(args[4]));
					runCommandCallback(sender,
							() -> sendMessage(sender, "&cAdded temporary permission to " + args[1] + " for " + args[4]));
				});
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "AddTempPermissions", "(Text)" },
				permPrefix + ".AddTempPermission", "Add temp permission") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					plugin.getPermissionHandler().addPermission(UUID.fromString(user.getUUID()), args[3]);
					runCommandCallback(sender,
							() -> sendMessage(sender, "&cAdded temporary permission to " + args[1]));
				});
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

				withResolvedUser(sender, args[1], user -> removeUserData(sender, args[1], user,
						() -> UuidLookup.getInstance().invalidate(user.getUUID())));
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "UserUUIDRemove", "(uuid)" }, permPrefix + ".UserRemove",
				"Remove User") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				sendMessage(sender, "&cRemoving " + args[1]);

				AdvancedCoreUser user = plugin.getUserManager().getUser(UUID.fromString(args[1]));
				removeUserData(sender, args[1], user, () -> UuidLookup.getInstance().invalidate(args[1]));
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
				runUserStorageCommand(sender, () -> plugin.getUserManager().purgeOldPlayersNow(),
						() -> sendMessage(sender, "&cPurged data"));
			}
		});

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

		if (plugin.getOptions().isJavascriptEngineEnabled() && plugin.getOptions().isJavascriptEngineCommandEnabled()) {
			cmds.add(new CommandHandler(plugin, new String[] { "Javascript", "(List)" }, permPrefix + ".Javascript",
					"Execute javascript") {

				@Override
				public void execute(CommandSender sender, String[] args) {
					if (sender.isOp()) {
						String str = "";
						for (int i = 1; i < args.length; i++) {
							str += args[i] + " ";
						}
						if (sender instanceof Player) {
							str = PlaceholderUtils.replacePlaceHolders((Player) sender, str);
						}
						JavascriptEngine engine = new JavascriptEngine();
						engine.addPlayer(sender);
						String javascript = str.trim();
						if (MessageAPI.containsIgnorecase(javascript, "powershell")
								|| MessageAPI.containsIgnorecase(javascript, "touch")
								|| MessageAPI.containsIgnorecase("Runtime-getRuntime()", str)) {
							sendMessage(sender, "&aNot allowed");
							plugin.getLogger()
									.warning("Player " + sender.getName() + " attempted to run shell commands");
							return;
						}
						sendMessage(sender, "&cJavascript result: " + engine.getStringValue(javascript));
					} else {
						sendMessage(sender, "&aNot allowed");
					}
				}
			});
		}

		cmds.add(new PlayerCommandHandler(plugin, new String[] { "User", "(player)", "SetData", "(text)", "(text)" },
				permPrefix + ".SetData", "Set user data") {

			@Override
			public void executeAll(CommandSender sender, String[] args) {
				String data = args[4];
				if (data.equalsIgnoreCase("\"\"")) {
					data = "";
				}

				final String key = args[3];
				final String value = data;

				runUserStorageCommand(sender, () -> plugin.getUserManager().forEachUserKeys((uuid, columns) -> {
					AdvancedCoreUser user = plugin.getUserManager().getUser(uuid, false);
					user.userDataFetechMode(UserDataFetchMode.NO_CACHE);
					user.getData().setString(key, value);
				}, null), () -> sender.sendMessage(MessageAPI.colorize("&cSet all users " + key + " to " + args[4])));
			}

			@Override
			public void executeSinglePlayer(CommandSender sender, String[] args) {
				String data = args[4];
				if (data.equalsIgnoreCase("\"\"")) data = "";
				final String value = data;
				withResolvedUser(sender, args[1], user -> {
					user.getData().setString(args[3], value);
					runCommandCallback(sender, () -> sender.sendMessage(
							MessageAPI.colorize("&cSet " + args[3] + " for " + args[1] + " to " + args[4])));
				});
			}
		}.withLegacyAllPermissionAliases(permPrefix + ".SetAllData"));

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ViewData" }, permPrefix + ".ViewData",
				"View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					if (plugin.getUserManager().getDataManager().deferSharedStorageResult(user.getData()::getValues,
							values -> values.forEach((key, value) ->
									sendMessage(sender, "&c&l" + key + " &c" + value.toString())),
							failure -> sendMessage(sender, "&cUnable to read user data; check the server log."),
							callbackOwner(sender))) return;
					for (Entry<String, DataValue> entry : user.getData().getValues().entrySet()) {
						sendMessage(sender, "&c&l" + entry.getKey() + " &c" + entry.getValue().toString());
					}
				});
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ViewCache" }, permPrefix + ".ViewCache",
				"View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					java.util.List<String> entries = user.getCache().displayCacheStringList();
					runCommandCallback(sender, () -> entries.forEach(sender::sendMessage));
				});

			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "ForceCache" },
				permPrefix + ".ForceCache", "View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> cacheUserAndReport(sender, args[1], user));
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "User", "(Player)", "HasPermission", "(Text)" },
				permPrefix + ".HasPermission", "View playerdata") {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[1], user -> {
					boolean hasPermission = user.hasPermission(args[3]);
					runCommandCallback(sender, () -> sendMessage(sender,
							"User " + args[1] + " permission " + args[3] + ":" + hasPermission));
				});
			}
		});

		cmds.add(new CommandHandler(plugin,
				new String[] { "Choices", "SetPreference", "(ChoiceReward)", "(String)", "(Player)" },
				permPrefix + ".ChoicesSetPreferenceOther", "Let user pick his choice preferences", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				withResolvedUser(sender, args[4], user -> {
					user.setChoicePreference(args[2], args[3]);
					user.sendMessage("&cPreference set to " + args[3] + " for " + args[4]);
				});
			}
		});

		if (plugin.isLoadUserData()) {
			cmds.add(new CommandHandler(plugin, new String[] { "ConvertToData", "(UserStorage)" },
					permPrefix + ".Commands.AdminVote.ConvertToData",
					"Convert user storage from current storage type to the one specified", true, true) {

				@Override
				public void execute(CommandSender sender, String[] args) {
					startStorageConversion(sender, plugin.getStorageType(), UserStorage.value(args[1]));
				}
			});

			cmds.add(new CommandHandler(plugin, new String[] { "ConvertFromData", "(UserStorage)" },
					permPrefix + ".Commands.AdminVote.ConvertFromData",
					"Convert user storage from the specified storage type to the current one", true, true) {

				@Override
				public void execute(CommandSender sender, String[] args) {
					startStorageConversion(sender, UserStorage.value(args[1]), plugin.getStorageType());
				}
			});
		}

		cmds.add(new CommandHandler(plugin, new String[] { "SetInputMethod", "(InputMethod)" },
				permPrefix + ".InputMethod", "Set your value request input method", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				Player player = (Player) sender;
				InputMethod method = InputMethod.getMethod(args[1]);

				PlayerInputManager.setInputMethod(player.getUniqueId(), method);

				sendMessage(sender, "&aInput method set to &e" + method.name());
			}
		});

		cmds.add(new CommandHandler(plugin, new String[] { "InputMethod" }, permPrefix + ".InputMethod",
				"Set your value request input method", false) {

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (!(sender instanceof Player)) {
					sendMessage(sender, "&cOnly players can use this command");
					return;
				}

				new ValueRequest(plugin, plugin.getDialogService()).openInputMethodSelection((Player) sender);
			}
		});

		for (CommandHandler cmd : cmds) {
			cmd.setAdvancedCoreCommand(true);
		}

		return cmds;
	}

	/** Run a shared-storage command off the primary thread and return UI work safely. */
	private void runUserStorageCommand(CommandSender sender, Runnable storageWork, Runnable onSuccess) {
		try {
			plugin.getBukkitScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					storageWork.run();
					if (onSuccess != null) runCommandCallback(sender, onSuccess);
				} catch (RuntimeException | Error failure) {
					if (plugin.getLogger() != null) {
						plugin.getLogger().severe("Bulk user operation failed (" + failure.getClass().getSimpleName() + ")");
					}
					runCommandCallback(sender, () -> sender.sendMessage(
							MessageAPI.colorize("&cUnable to process user storage; check the server log.")));
				}
			});
		} catch (RuntimeException | Error failure) {
			if (plugin.getLogger() != null) {
				plugin.getLogger().severe("Unable to schedule bulk user operation (" + failure.getClass().getSimpleName() + ")");
			}
			sender.sendMessage(MessageAPI.colorize("&cUnable to process user storage; check the server log."));
		}
	}

	private void runCommandCallback(CommandSender sender, Runnable callback) {
		org.bukkit.entity.Entity owner = callbackOwner(sender);
		if (owner == null) plugin.getBukkitScheduler().runTask(plugin, callback);
		else plugin.getBukkitScheduler().runTask(plugin, callback, owner);
	}

	private void reportForcedReplayCompletion(CommandSender sender, String playerName, Throwable failure) {
		runCommandCallback(sender, () -> {
			if (failure == null) {
				sender.sendMessage(MessageAPI.colorize(playerName == null ? "&cFinished running offline rewards"
						: "&cFinished running offline rewards for " + playerName));
				return;
			}
			if (plugin.getLogger() != null) plugin.getLogger().severe(
					"Forced offline reward replay failed (" + failure.getClass().getSimpleName() + ")");
			sender.sendMessage(MessageAPI.colorize("&cUnable to run offline rewards; check the server log."));
		});
	}

	private static final class ForcedReplayBarrier {
		private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(1);
		private final java.util.concurrent.atomic.AtomicReference<Throwable> firstFailure =
				new java.util.concurrent.atomic.AtomicReference<>();
		private final Consumer<Throwable> completion;

		private ForcedReplayBarrier(Consumer<Throwable> completion) {
			this.completion = completion;
		}

		private void add(java.util.concurrent.CompletionStage<Void> replay) {
			pending.incrementAndGet();
			replay.whenComplete((ignored, failure) -> {
				if (failure != null) firstFailure.compareAndSet(null, failure);
				completeOne();
			});
		}

		private void enumerationComplete() {
			completeOne();
		}

		private void completeOne() {
			if (pending.decrementAndGet() == 0) completion.accept(firstFailure.get());
		}
	}

	/**
	 * Resolve a recipient from the global scheduler, then run player-affine work
	 * on that recipient's region. Offline users retain the global fallback.
	 */
	private void runRecipientCallback(UUID uuid, Runnable callback) {
		plugin.getBukkitScheduler().runTask(plugin, () -> {
			Player recipient = Bukkit.getPlayer(uuid);
			if (recipient == null) callback.run();
			else plugin.getBukkitScheduler().runTask(plugin, callback, recipient);
		});
	}

	private void startStorageConversion(CommandSender sender, UserStorage from, UserStorage to) {
		sender.sendMessage(MessageAPI.colorize("&cStarting convert from " + from + " to " + to));
		plugin.convertDataStorageAsync(from, to).whenComplete((ignored, failure) -> {
			Runnable completion = () -> {
				if (failure == null) {
					sender.sendMessage(MessageAPI.colorize("&cFinished converting"));
					return;
				}
				// JDBC/provider exceptions can embed connection details. The storage
				// layer records safe diagnostics; do not expose the raw exception here.
				plugin.getLogger().severe("User storage conversion failed ("
						+ failure.getClass().getSimpleName() + ")");
				sender.sendMessage(MessageAPI.colorize("&cUser storage conversion failed; see the server log"));
			};
			runCommandCallback(sender, completion);
		});
	}

	/** Complete destructive user removal before reporting success or clearing identity mappings. */
	private void removeUserData(CommandSender sender, String identifier, AdvancedCoreUser user,
			Runnable afterRemoval) {
		java.util.function.Supplier<Boolean> remove = () -> {
			user.getData().remove();
			return Boolean.TRUE;
		};
		java.util.function.Consumer<Boolean> succeeded = ignored -> {
			afterRemoval.run();
			sender.sendMessage(MessageAPI.colorize("&cRemoved " + identifier));
		};
		java.util.function.Consumer<Throwable> failed = ignored ->
			sender.sendMessage(MessageAPI.colorize("&cUnable to remove " + identifier + "; check the server log."));
		if (plugin.getUserManager().getDataManager().deferSharedStorageResult(remove, succeeded, failed,
				callbackOwner(sender))) return;
		try { succeeded.accept(remove.get()); }
		catch (RuntimeException failure) {
			plugin.getLogger().severe("User removal failed (" + failure.getClass().getSimpleName() + ")");
			failed.accept(failure);
		}
	}

	/** Complete cache population before acknowledging the administrative command. */
	void cacheUserAndReport(CommandSender sender, String identifier, AdvancedCoreUser user) {
		java.util.function.Supplier<Boolean> populate = () -> {
			user.cache();
			return Boolean.TRUE;
		};
		java.util.function.Consumer<Boolean> succeeded = ignored ->
				sender.sendMessage(MessageAPI.colorize("&aForced cached " + identifier));
		java.util.function.Consumer<Throwable> failed = ignored ->
				sender.sendMessage(MessageAPI.colorize(
						"&cUnable to cache " + identifier + "; check the server log."));
		try {
			if (plugin.getUserManager().getDataManager().deferSharedStorageResult(populate, succeeded, failed,
					callbackOwner(sender))) return;
			succeeded.accept(populate.get());
		}
		catch (RuntimeException | Error failure) {
			plugin.getLogger().severe("User cache population failed (" + failure.getClass().getSimpleName() + ")");
			failed.accept(failure);
		}
	}

	private org.bukkit.entity.Entity callbackOwner(CommandSender sender) {
		return sender instanceof Player player ? player : null;
	}

	/**
	 * Gets the basic commands.
	 *
	 * @param permPrefix the permission prefix
	 * @return the basic commands
	 */
	public ArrayList<CommandHandler> getBasicCommands(String permPrefix) {
		ArrayList<CommandHandler> cmds = new ArrayList<>();

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
}
