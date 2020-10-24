package com.Ben12345rocks.AdvancedCore.Util.ValueRequest;

import java.util.LinkedHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.BooleanRequester;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.NumberRequester;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Requesters.StringRequester;

/**
 * The Class ValueRequest.
 */
public class ValueRequest {

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The method. */
	private InputMethod method = null;

	/**
	 * Instantiates a new value request.
	 */
	public ValueRequest() {
	}

	/**
	 * Instantiates a new value request.
	 *
	 * @param method the method
	 */
	public ValueRequest(InputMethod method) {
		this.method = method;
	}

	/**
	 * Request boolean.
	 *
	 * @param player   the player
	 * @param listener the listener
	 */
	public void requestBoolean(Player player, BooleanListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new BooleanRequester().request(player, input, "", "Type cancel to cancel", listener);
	}

	/**
	 * Request boolean.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param listener     the listener
	 */
	public void requestBoolean(Player player, String currentValue, BooleanListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new BooleanRequester().request(player, input, currentValue, "Type cancel to cancel", listener);
	}

	public void requestNumber(Player player, LinkedHashMap<Number, ItemStack> options, String currentValue,
			boolean allowCustomOption, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, currentValue, options, "Type cancel to cancel", allowCustomOption,
				listener);
	}

	/**
	 * Request number.
	 *
	 * @param player   the player
	 * @param listener the listener
	 */
	public void requestNumber(Player player, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, "", "Type cancel to cancel", null, true, listener);
	}

	/**
	 * Request number.
	 *
	 * @param player            the player
	 * @param currentValue      the current value
	 * @param options           the options
	 * @param allowCustomOption the allow custom option
	 * @param listener          the listener
	 */
	public void requestNumber(Player player, String currentValue, Number[] options, boolean allowCustomOption,
			NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, currentValue, "Type cancel to cancel", options, allowCustomOption,
				listener);
	}

	/**
	 * Request number.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param options      the options
	 * @param listener     the listener
	 */
	public void requestNumber(Player player, String currentValue, Number[] options, NumberListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new NumberRequester().request(player, input, currentValue, "Type cancel to cancel", options, true, listener);
	}

	public void requestString(Player player, LinkedHashMap<String, ItemStack> options, String currentValue,
			boolean allowCustomOption, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new StringRequester().request(player, input, currentValue, options, "Type cancel to cancel", allowCustomOption,
				listener);
	}

	/**
	 * Request string.
	 *
	 * @param player            the player
	 * @param currentValue      the current value
	 * @param options           the options
	 * @param allowCustomOption the allow custom option
	 * @param listener          the listener
	 */
	public void requestString(Player player, String currentValue, String[] options, boolean allowCustomOption,
			StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new StringRequester().request(player, input, currentValue, "Type cancel to cancel", options, allowCustomOption,
				listener);
	}

	/**
	 * Request string.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param options      the options
	 * @param listener     the listener
	 */
	public void requestString(Player player, String currentValue, String[] options, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new StringRequester().request(player, input, currentValue, "Type cancel to cancel", options, true, listener);
	}

	/**
	 * Request string.
	 *
	 * @param player   the player
	 * @param listener the listener
	 */
	public void requestString(Player player, StringListener listener) {
		InputMethod input = method;
		if (input == null) {
			input = UserManager.getInstance().getUser(player).getUserInputMethod();
		}
		new StringRequester().request(player, input, "", "Type cancel to cancel", null, true, listener);
	}

}
