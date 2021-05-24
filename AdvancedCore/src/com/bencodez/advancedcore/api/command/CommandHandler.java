package com.bencodez.advancedcore.api.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.user.UserManager;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandHandler.
 */
public abstract class CommandHandler {

	@Getter
	@Setter
	private boolean advancedCoreCommand = false;

	@Getter
	@Setter
	private boolean allowConsole = true;

	@Getter
	@Setter
	private boolean allowMultiplePermissions;

	@Getter
	@Setter
	private String[] args;

	@Getter
	@Setter
	private boolean forceConsole = false;

	@Getter
	@Setter
	private String helpMessage;

	private boolean ignoreNumberCheck = false;

	@Getter
	@Setter
	private String perm;

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	public CommandHandler() {
		allowMultiplePermissions = plugin.getOptions().isMultiplePermissionChecks();
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param args the args
	 * @param perm the perm
	 */
	public CommandHandler(String[] args, String perm) {
		this.args = args;
		this.perm = perm;
		helpMessage = "Unknown Help Message";
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param args        the args
	 * @param perm        the perm
	 * @param helpMessage the help message
	 */
	public CommandHandler(String[] args, String perm, String helpMessage) {
		this.args = args;
		this.perm = perm;
		this.helpMessage = helpMessage;
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param args         the args
	 * @param perm         the perm
	 * @param helpMessage  the help message
	 * @param allowConsole the allow console
	 */
	public CommandHandler(String[] args, String perm, String helpMessage, boolean allowConsole) {
		this.args = args;
		this.perm = perm;
		this.helpMessage = helpMessage;
		this.allowConsole = allowConsole;
	}

	public CommandHandler(String[] args, String perm, String helpMessage, boolean allowConsole, boolean forceConsole) {
		this.args = args;
		this.perm = perm;
		this.helpMessage = helpMessage;
		this.allowConsole = allowConsole;
		this.forceConsole = forceConsole;
	}

	/**
	 * Adds the tab complete option.
	 *
	 * @param toReplace the to replace
	 * @param options   the options
	 */
	@Deprecated
	public void addTabCompleteOption(String toReplace, ArrayList<String> options) {
		TabCompleteHandler.getInstance().addTabCompleteOption(toReplace, options);
	}

	/**
	 * Adds the tab complete option.
	 *
	 * @param toReplace the to replace
	 * @param options   the options
	 */
	@Deprecated
	public void addTabCompleteOption(String toReplace, String... options) {
		addTabCompleteOption(toReplace, ArrayUtils.getInstance().convert(options));
	}

	/**
	 * Args match.
	 *
	 * @param arg the arg
	 * @param i   the i
	 * @return true, if successful
	 */
	public boolean argsMatch(String arg, int i) {
		if (i < args.length) {
			String[] cmdArgs = args[i].split("&");
			for (String cmdArg : cmdArgs) {
				if (arg.equalsIgnoreCase(cmdArg)) {
					return true;
				}

				for (String str : TabCompleteHandler.getInstance().getTabCompleteReplaces()) {
					if (str.equalsIgnoreCase(cmdArg)) {
						return true;
					}
				}

			}
			// plugin.debug("Tab: "
			// + Utils.getInstance().makeStringList(
			// Utils.getInstance().convert(
			// tabCompleteOptions.keySet())) + " "
			// + args[i]);
			for (String str : TabCompleteHandler.getInstance().getTabCompleteReplaces()) {
				if (str.equalsIgnoreCase(args[i])) {
					return true;
				}
			}
			return false;
		} else if (args[args.length - 1].equalsIgnoreCase("(list)")) {
			return true;
		}
		return false;
	}

	/**
	 * Execute.
	 *
	 * @param sender the sender
	 * @param args   the args
	 */
	public abstract void execute(CommandSender sender, String[] args);

	/**
	 * Gets the help line.
	 *
	 * @param command the command
	 * @return the help line
	 */
	public TextComponent getHelpLine(String command) {
		String line = plugin.getOptions().getHelpLine();

		String commandText = getHelpLineCommand(command);
		line = line.replace("%Command%", commandText);
		if (getHelpMessage() != "") {
			line = line.replace("%HelpMessage%", getHelpMessage());
		}
		TextComponent txt = StringParser.getInstance().stringToComp(line);
		txt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandText));
		txt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(getHelpMessage()).color(ChatColor.AQUA).create()));
		return txt;

	}

	public TextComponent getHelpLine(String command, String line) {
		String commandText = getHelpLineCommand(command);
		line = line.replace("%Command%", commandText);
		if (getHelpMessage() != "") {
			line = line.replace("%HelpMessage%", getHelpMessage());
		}
		TextComponent txt = StringParser.getInstance().stringToComp(line);
		txt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandText));
		txt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(getHelpMessage()).color(ChatColor.AQUA).create()));
		return txt;
	}
	
	public TextComponent getHelpLine(String command, String line, ChatColor hoverColor) {
		String commandText = getHelpLineCommand(command);
		line = line.replace("%Command%", commandText);
		if (getHelpMessage() != "") {
			line = line.replace("%HelpMessage%", getHelpMessage());
		}
		TextComponent txt = StringParser.getInstance().stringToComp(line);
		txt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandText));
		txt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(getHelpMessage()).color(hoverColor).create()));
		return txt;
	}

	/**
	 * Gets the help line command.
	 *
	 * @param command the command
	 * @return the help line command
	 */
	public String getHelpLineCommand(String command) {
		String commandText = command;
		boolean addSpace = true;
		if (command.isEmpty()) {
			addSpace = false;
		}
		for (String arg1 : args) {
			int count = 1;
			for (String arg : arg1.split("&")) {
				if (count == 1) {
					if (addSpace) {
						commandText += " " + arg;
					} else {
						commandText += arg;
						addSpace = true;
					}
				} else {
					commandText += "/" + arg;
				}
				count++;
			}
		}
		return commandText;
	}

	public ArrayList<String> getTabCompleteOptions(CommandSender sender, String[] args, int argNum,
			ConcurrentHashMap<String, ArrayList<String>> tabCompleteOptions) {
		Set<String> cmds = new HashSet<String>();
		if (hasPerm(sender)) {
			CommandHandler commandHandler = this;

			String[] cmdArgs = commandHandler.getArgs();
			if (cmdArgs.length > argNum) {
				boolean argsMatch = true;
				for (int i = 0; i < argNum; i++) {
					if (args.length >= i) {
						if (!commandHandler.argsMatch(args[i], i)) {
							argsMatch = false;
						}
					}
				}

				if (argsMatch) {
					String[] cmdArgsList = cmdArgs[argNum].split("&");

					for (String arg : cmdArgsList) {
						// plugin.debug(arg);
						boolean add = true;
						for (Entry<String, ArrayList<String>> entry : tabCompleteOptions.entrySet()) {
							if (arg.equalsIgnoreCase(entry.getKey())) {
								add = false;
								cmds.addAll(entry.getValue());
							}
						}
						if (!cmds.contains(arg) && add) {
							cmds.add(arg);
						}
					}

				}

			}
		}

		ArrayList<String> options = ArrayUtils.getInstance().convert(cmds);

		Collections.sort(options, String.CASE_INSENSITIVE_ORDER);

		return options;
	}

	public boolean hasArg(String arg) {
		for (String str : getArgs()) {
			if (str.equalsIgnoreCase(arg)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasPerm(CommandSender sender) {
		if (allowMultiplePermissions) {
			return PlayerUtils.getInstance().hasEitherPermission(sender, getPerm());
		} else {
			return sender.hasPermission(getPerm().split(Pattern.quote("|"))[0]);
		}
	}

	public CommandHandler ignoreNumberCheck() {
		ignoreNumberCheck = true;
		return this;
	}

	public boolean isCommand(String arg) {
		if (getArgs().length > 0) {
			for (String str : getArgs()[0].split("&")) {
				if (str.equalsIgnoreCase(arg)) {
					return true;
				}
			}

		} else if (arg.isEmpty() && getArgs().length == 0) {
			return true;
		}
		return false;
	}

	public boolean isPlayer(CommandSender sender) {
		return sender instanceof Player;
	}

	public CommandHandler noConsole() {
		this.allowConsole = false;
		return this;
	}

	public int parseInt(String arg) {
		return Integer.parseInt(arg);
	}

	/**
	 * Run command.
	 *
	 * @param sender the sender
	 * @param args   the args
	 * @return true, if successful
	 */
	public boolean runCommand(CommandSender sender, String[] args) {
		if (args.length >= this.args.length) {
			if (this.args.length != args.length && !hasArg("(list)")) {
				return false;
			}
			for (int i = 0; i < args.length && i < this.args.length; i++) {
				if (!argsMatch(args[i], i)) {
					return false;
				}
				if (this.args[i].equalsIgnoreCase("(number)")) {
					if (!ignoreNumberCheck && !StringParser.getInstance().isInt(args[i])) {
						sender.sendMessage(StringParser.getInstance()
								.colorize(plugin.getOptions().getFormatNotNumber().replace("%arg%", args[i])));
						return true;
					}
				} else if (this.args[i].equalsIgnoreCase("(Player)")) {
					if (args[i].equalsIgnoreCase("@p")) {
						args[i] = sender.getName();
					} else if (args[i].equalsIgnoreCase("@r")) {
						args[i] = PlayerUtils.getInstance().getRandomOnlinePlayer().getName();
					}
				}
			}
			if (!(sender instanceof Player) && !allowConsole) {
				sender.sendMessage(StringParser.getInstance().colorize("&cMust be a player to do this"));
				return true;
			}

			if (sender instanceof Player && forceConsole) {
				sender.sendMessage(StringParser.getInstance().colorize("&cConsole command only"));
				return true;
			}

			if (!hasPerm(sender)) {
				sender.sendMessage(StringParser.getInstance().colorize(plugin.getOptions().getFormatNoPerms()));
				plugin.getLogger().log(Level.INFO,
						sender.getName() + " was denied access to command, required permission: " + perm);
				return true;
			}

			if (args.length > 0 && args[0].equalsIgnoreCase("AdvancedCore")) {
				String[] list = new String[args.length - 1];
				for (int i = 1; i < args.length; i++) {
					list[i - 1] = args[i];
				}
				args = list;
			}
			String[] argsNew = args;

			Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					execute(sender, argsNew);
				}
			});

			return true;
		}
		return false;
	}

	public void sendMessage(CommandSender sender, ArrayList<String> msg) {
		sender.sendMessage(ArrayUtils.getInstance().convert(ArrayUtils.getInstance().colorize(msg)));
	}

	public void sendMessage(CommandSender sender, String msg) {
		sender.sendMessage(StringParser.getInstance().colorize(msg));
	}

	public void sendMessageJson(CommandSender sender, ArrayList<TextComponent> comp) {
		if (isPlayer(sender)) {
			Player player = (Player) sender;
			UserManager.getInstance().getUser(player).sendJson(comp);
		} else {
			sender.sendMessage(ArrayUtils.getInstance().convert(ArrayUtils.getInstance().comptoString(comp)));
		}
	}

	public void sendMessageJson(CommandSender sender, TextComponent comp) {
		if (isPlayer(sender)) {
			Player player = (Player) sender;
			UserManager.getInstance().getUser(player).sendJson(comp);
		} else {
			sender.sendMessage(StringParser.getInstance().compToString(comp));
		}
	}

	public CommandHandler withArgs(String... args) {
		this.args = args;
		return this;
	}

	public CommandHandler withHelpMessage(String helpMessage) {
		this.helpMessage = helpMessage;
		return this;
	}

	public CommandHandler withPerm(String perm) {
		if (!this.perm.isEmpty()) {
			this.perm = this.perm + "|" + perm;
		} else {
			this.perm = perm;
		}
		return this;
	}

	public CommandHandler withPerm(String perm, boolean add) {
		if (!add) {
			return this;
		}
		if (!this.perm.isEmpty()) {
			this.perm = this.perm + "|" + perm;
		} else {
			this.perm = perm;
		}
		return this;
	}

}
