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

	private InputMethod method = null;

	public ValueRequest() {
	}

	public ValueRequest(InputMethod method) {
		this.method = method;
	}

	public void requestString(Player player, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new StringRequester().request(player, input, "",
				"Type cancel to cancel", null, true, listener);
	}

	public void requestString(Player player, String currentValue,
			String[] options, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new StringRequester().request(player, input, currentValue,
				"Type cancel to cancel", options, true, listener);
	}

	public void requestString(Player player, String currentValue,
			String[] options, boolean allowCustomOption, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new StringRequester().request(player, input, currentValue,
				"Type cancel to cancel", options, allowCustomOption, listener);
	}

	public void requestNumber(Player player, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, "",
				"Type cancel to cancel", null, true, listener);
	}

	public void requestNumber(Player player, String currentValue,
			Number[] options, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, currentValue,
				"Type cancel to cancel", options, true, listener);
	}

	public void requestNumber(Player player, String currentValue,
			Number[] options, boolean allowCustomOption, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, currentValue,
				"Type cancel to cancel", options, allowCustomOption, listener);
	}

	public void requestBoolean(Player player, BooleanListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new BooleanRequester().request(player, input, "",
				"Type cancel to cancel", listener);
	}

	public void requestBoolean(Player player, String currentValue,
			BooleanListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = new User(Main.plugin, player).getUserInputMethod();
		}
		new BooleanRequester().request(player, input, currentValue,
				"Type cancel to cancel", listener);
	}

}
