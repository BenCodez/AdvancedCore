package com.bencodez.advancedcore.api.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.bencodez.advancedcore.AdvancedCorePlugin;

public abstract class PlayerCommandHandler extends CommandHandler {

	private static final String ALL_SELECTOR_SENTINEL = new String("__advancedcore_all_selector__");
	private int playerArg = -1;
	private final Set<String> allPermissionOverrides = new LinkedHashSet<>();
	private final Set<String> legacyAllPermissionAliases = new LinkedHashSet<>();
	private final ThreadLocal<Boolean> legacyBulkDispatch = ThreadLocal.withInitial(() -> Boolean.FALSE);

	public PlayerCommandHandler(AdvancedCorePlugin plugin) {
		super(plugin);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm) {
		super(plugin, args, perm);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage) {
		super(plugin, args, perm, helpMessage);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole) {
		super(plugin, args, perm, helpMessage, allowConsole);
		figureOutPlayerArg();
	}

	public PlayerCommandHandler(AdvancedCorePlugin plugin, String[] args, String perm, String helpMessage,
			boolean allowConsole, boolean forceConsole) {
		super(plugin, args, perm, helpMessage, allowConsole, forceConsole);
		figureOutPlayerArg();
	}

	@Override
	public boolean runCommand(CommandSender sender, String[] args) {
		if (args == null) return false;
		if (playerArg < 0 || playerArg >= args.length
				|| !"all".equalsIgnoreCase(args[playerArg])) return super.runCommand(sender, args);
		String[] preservedArgs = args.clone();
		preservedArgs[playerArg] = ALL_SELECTOR_SENTINEL;
		boolean legacyAlias = hasLegacyAllPermissionAlias(sender);
		if (legacyAlias) legacyBulkDispatch.set(Boolean.TRUE);
		try {
			return super.runCommand(sender, preservedArgs);
		} finally {
			if (legacyAlias) legacyBulkDispatch.remove();
		}
	}

	@Override
	public boolean hasPerm(CommandSender sender) {
		return Boolean.TRUE.equals(legacyBulkDispatch.get()) && hasLegacyAllPermissionAlias(sender)
				|| super.hasPerm(sender);
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		String[] schema = getArgs();
		if (playerArg < 0 || schema == null || args == null || args.length < schema.length
				|| playerArg >= args.length) {
			return;
		}
		if (args[playerArg] == null || args[playerArg].isBlank()) return;
		if (args[playerArg].equalsIgnoreCase("all") || args[playerArg] == ALL_SELECTOR_SENTINEL) {
			if (hasAllPermission(sender)) {
				if (args[playerArg] == ALL_SELECTOR_SENTINEL) args[playerArg] = "all";
				executeAll(sender, args);
			} else {
				String noPermission = formatNoPerms();
				if (noPermission != null && !noPermission.isEmpty()) sendMessage(sender, noPermission);
			}
			return;
		}
		executeSinglePlayer(sender, args);
	}

	public abstract void executeAll(CommandSender sender, String[] args);

	public abstract void executeSinglePlayer(CommandSender sender, String[] args);

	/**
	 * Checks the complete authorization required for the special {@code all} target.
	 * Ordinary command authorization is required first, followed by either one of
	 * the dedicated bulk permissions or an explicitly configured administrator
	 * override. Overrides honor the handler's multiple-permission setting.
	 *
	 * @param sender command sender
	 * @return whether bulk execution is authorized
	 */
	public boolean hasAllPermission(CommandSender sender) {
		List<String> configured = configuredPermissions();
		if (sender == null || configured.isEmpty()) return false;
		if (hasLegacyAllPermissionAlias(sender)) return true;
		if (!hasPerm(sender)) return false;
		int limit = isAllowMultiplePermissions() ? configured.size() : 1;
		for (int i = 0; i < limit; i++) {
			String permission = configured.get(i);
			// Keep the granular permission and its bulk node paired. A bulk node
			// for one alternative must not combine with the base node of another.
			if (!sender.hasPermission(permission)) continue;
			if (allPermissionOverrides.contains(permission)
					|| sender.hasPermission(permission + ".All")) return true;
		}
		return false;
	}

	private boolean hasLegacyAllPermissionAlias(CommandSender sender) {
		if (sender == null) return false;
		for (String alias : legacyAllPermissionAliases) {
			if (sender.hasPermission(alias)) return true;
		}
		return false;
	}

	/**
	 * Returns permissions used by special player targets in addition to the normal
	 * command permission. Permission-listing commands can use this without granting
	 * the bulk permission during ordinary command checks.
	 *
	 * @return dedicated permissions for the {@code all} target
	 */
	public List<String> getAdditionalPermissions() {
		List<String> configured = configuredPermissions();
		if (configured.isEmpty()) return Collections.emptyList();
		ArrayList<String> permissions = derivedAllPermissions(configured, isAllowMultiplePermissions());
		permissions.addAll(legacyAllPermissionAliases);
		return Collections.unmodifiableList(permissions);
	}

	private ArrayList<String> derivedAllPermissions(List<String> configured, boolean includeAlternatives) {
		LinkedHashSet<String> permissions = new LinkedHashSet<>();
		int limit = includeAlternatives ? configured.size() : 1;
		for (int i = 0; i < limit; i++) {
			String permission = configured.get(i);
			if (!allPermissionOverrides.contains(permission)) permissions.add(permission + ".All");
		}
		return new ArrayList<>(permissions);
	}

	/**
	 * Marks configured permission alternatives that are full administrator
	 * overrides, rather than granular permissions which need their own
	 * {@code .All} node. Unknown or malformed values are ignored and can never
	 * grant bulk access.
	 *
	 * @param permissions configured administrator permission alternatives
	 * @return this handler
	 */
	public PlayerCommandHandler withAllPermissionOverrides(String... permissions) {
		allPermissionOverrides.clear();
		List<String> configured = configuredPermissions();
		if (permissions == null || configured.isEmpty()) return this;
		for (String permission : permissions) {
			if (permission != null && configured.contains(permission)) allPermissionOverrides.add(permission);
		}
		return this;
	}

	/**
	 * Retains a previously published permission that represented the complete bulk
	 * operation by itself. Unlike a normal configured alternative, an alias never
	 * grants named-player execution.
	 */
	public PlayerCommandHandler withLegacyAllPermissionAliases(String... permissions) {
		legacyAllPermissionAliases.clear();
		if (permissions == null) return this;
		for (String permission : permissions) {
			if (permission != null && !permission.isBlank() && !permission.endsWith(".All")
					&& permission.chars().noneMatch(Character::isWhitespace)) {
				legacyAllPermissionAliases.add(permission);
			}
		}
		return this;
	}

	/** Returns the valid administrator alternatives used by bulk authorization. */
	public List<String> getAllPermissionOverrides() {
		return Collections.unmodifiableList(configuredAllPermissionOverrides());
	}

	private ArrayList<String> configuredAllPermissionOverrides() {
		ArrayList<String> overrides = new ArrayList<>();
		for (String permission : configuredPermissions()) {
			if (allPermissionOverrides.contains(permission)) overrides.add(permission);
		}
		return overrides;
	}

	private List<String> configuredPermissions() {
		String permission = getPerm();
		if (permission == null || permission.isBlank()) return Collections.emptyList();
		String[] values = permission.split(Pattern.quote("|"), -1);
		ArrayList<String> permissions = new ArrayList<>(values.length);
		for (String value : values) {
			if (value.isBlank() || value.endsWith(".All")
					|| value.chars().anyMatch(Character::isWhitespace)) return Collections.emptyList();
			permissions.add(value);
		}
		return permissions;
	}

	private void figureOutPlayerArg() {
		playerArg = -1;
		String[] args = getArgs();
		if (args == null) return;
		for (int i = 0; i < args.length; i++) {
			if ("(player)".equalsIgnoreCase(args[i])) {
				playerArg = i;
				return;
			}
		}
		getPlugin().devDebug("Failed to figure out player arg number for: " + java.util.Arrays.toString(args));
	}

	@Override
	public void setArgs(String[] args) {
		super.setArgs(args);
		figureOutPlayerArg();
	}

}
