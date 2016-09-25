package com.Ben12345rocks.AdvancedCore.Util.ValueRequest;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.BooleanRequester;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.NumberRequester;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.StringRequester;

public class ValueRequest {
	/** The plugin. */
	static Main plugin = Main.plugin;

	public ValueRequest() {

	}

	public void requestString(Player player, StringListener listener) {
		new StringRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(), "",
				"Type cancel to cancel", null, listener);
	}

	public void requestString(Player player, String currentValue,
			String[] options, StringListener listener) {
		new StringRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(),
				currentValue, "Type cancel to cancel", options, listener);
	}

	public void requestNumber(Player player, NumberListener listener) {
		new NumberRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(), "",
				"Type cancel to cancel", null, listener);
	}

	public void requestNumber(Player player, String currentValue,
			Number[] options, NumberListener listener) {
		new NumberRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(),
				currentValue, "Type cancel to cancel", options, listener);
	}

	public void requestBoolean(Player player, BooleanListener listener) {
		new BooleanRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(), "",
				"Type cancel to cancel", listener);
	}

	public void requestBoolean(Player player, String currentValue,
			BooleanListener listener) {
		new BooleanRequester().request(player,
				new User(Main.plugin, player).getUserInputMethod(),
				currentValue, "Type cancel to cancel", listener);
	}

}
