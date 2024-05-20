package com.bencodez.advancedcore.api.command;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.command.ICommand;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

import lombok.Getter;

// TODO: Auto-generated Javadoc
/**
 * The Class CommandHandler.
 */
public abstract class CommandHandler extends com.bencodez.simpleapi.command.CommandHandler implements ICommand {

	@Getter
	private AdvancedCorePlugin plugin;

	@Deprecated
	public CommandHandler() {
		super(AdvancedCorePlugin.getInstance());
		this.plugin = AdvancedCorePlugin.getInstance();
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	@Deprecated
	public CommandHandler(String[] args, String perm) {
		super(AdvancedCorePlugin.getInstance(), args, perm);
		this.plugin = AdvancedCorePlugin.getInstance();
		setHelpMessage("Unknown Help Message");
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());

	}

	@Deprecated
	public CommandHandler(String[] args, String perm, String helpMessage) {
		super(AdvancedCorePlugin.getInstance(), args, perm, helpMessage);
		this.plugin = AdvancedCorePlugin.getInstance();
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	@Deprecated
	public CommandHandler(String[] args, String perm, String helpMessage, boolean allowConsole) {
		super(AdvancedCorePlugin.getInstance(), args, perm, helpMessage, allowConsole);
		this.plugin = AdvancedCorePlugin.getInstance();
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	@Deprecated
	public CommandHandler(String[] args, String perm, String helpMessage, boolean allowConsole, boolean forceConsole) {
		super(AdvancedCorePlugin.getInstance(), args, perm, helpMessage, allowConsole, forceConsole);
		this.plugin = AdvancedCorePlugin.getInstance();

		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	public CommandHandler(AdvancedCorePlugin plugin) {
		super(plugin);
		this.plugin = plugin;
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param plugin main pluginclass
	 * @param args   the args
	 * @param perm   the perm
	 */
	public CommandHandler(AdvancedCorePlugin plugin, String[] args, String perm) {
		super(plugin, args, perm);
		this.plugin = plugin;
		setHelpMessage("Unknown Help Message");
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());

	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param plugin      main pluginclass
	 * @param args        the args
	 * @param perm        the perm
	 * @param helpMessage the help message
	 */
	public CommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage) {
		super(plugin, args, perm, helpMessage);
		this.plugin = plugin;
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param plugin       main pluginclass
	 * @param args         the args
	 * @param perm         the perm
	 * @param helpMessage  the help message
	 * @param allowConsole the allow console
	 */
	public CommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole) {
		super(plugin, args, perm, helpMessage, allowConsole);
		this.plugin = plugin;
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	/**
	 * Instantiates a new command handler.
	 *
	 * @param plugin       main pluginclass
	 * @param args         the args
	 * @param perm         the perm
	 * @param helpMessage  the help message
	 * @param allowConsole the allow console
	 * @param forceConsole Option to force console only command
	 */
	public CommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole, boolean forceConsole) {
		super(plugin, args, perm, helpMessage, allowConsole, forceConsole);
		this.plugin = plugin;
		setAllowMultiplePermissions(plugin.getOptions().isMultiplePermissionChecks());
	}

	@Override
	public CommandHandler ignoreNumberCheck() {
		super.ignoreNumberCheck();
		return this;
	}

	@Override
	public String getHelpLine() {
		return plugin.getOptions().getHelpLine();
	}

	@Override
	public void debug(String debug) {
		plugin.debug(debug);
	}

	@Override
	public String formatNotNumber() {
		return plugin.getOptions().getFormatNotNumber();
	}

	@Override
	public String formatNoPerms() {
		return plugin.getOptions().getFormatNoPerms();
	}

	@Override
	public BukkitScheduler getBukkitScheduler() {
		return plugin.getBukkitScheduler();
	}

}
