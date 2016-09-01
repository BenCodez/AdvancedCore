package com.Ben12345rocks.AdvancedCore.Commands;

import java.util.ArrayList;

import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Objects.CommandHandler;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.Request.RequestManager;
import com.Ben12345rocks.AdvancedCore.Util.Request.RequestManager.InputMethod;

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
		plugin.advancedCoreCommands.add(new CommandHandler(new String[] {
				"Reload"}, "AdvancedCore.Reload" , "Reload the plugin") {

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
					user.setInputMethod(InputMethod.valueOf(args[1]));
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

		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				com.Ben12345rocks.AdvancedCore.Thread.Thread.getInstance().run(
						new Runnable() {

							@Override
							public void run() {
								loadTabComplete();
							}
						});
			}
		});
	}

	/**
	 * Load tab complete.
	 */
	public void loadTabComplete() {
		ArrayList<String> method = new ArrayList<String>();
		for (InputMethod me : RequestManager.InputMethod.values()) {
			method.add(me.toString());
		}
		for (int i = 0; i < plugin.advancedCoreCommands.size(); i++) {
			plugin.advancedCoreCommands.get(i).addTabCompleteOption(
					"(RequestMethod)", method);
		}
	}

}
