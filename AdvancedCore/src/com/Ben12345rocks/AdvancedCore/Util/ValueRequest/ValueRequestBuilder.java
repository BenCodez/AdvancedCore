package com.Ben12345rocks.AdvancedCore.Util.ValueRequest;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.NumberListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

public class ValueRequestBuilder {

	private StringListener stringListener;
	private NumberListener numberListener;
	private BooleanListener booleanListener;
	private String[] stringOptions;
	private Number[] numberOptions;
	private InputMethod method = null;
	private String currentValue = "";
	private boolean allowCustomOption = false;

	public ValueRequestBuilder(StringListener listener, String[] options) {
		this.stringListener = listener;
		this.stringOptions = options;
	}

	public ValueRequestBuilder(NumberListener listener, Number[] options) {
		this.numberListener = listener;
		this.numberOptions = options;
	}

	public ValueRequestBuilder(BooleanListener listener) {
		this.booleanListener = listener;
	}

	public ValueRequestBuilder usingMethod(InputMethod method) {
		this.method = method;
		return this;
	}

	public ValueRequestBuilder currentValue(String currentValue) {
		this.currentValue = currentValue;
		return this;
	}

	public ValueRequestBuilder allowCustomOption(boolean allowCustomOption) {
		this.allowCustomOption = allowCustomOption;
		return this;
	}

	public void request(Player player) {
		if (numberListener != null) {
			new ValueRequest(method).requestNumber(player, currentValue, numberOptions, allowCustomOption,
					numberListener);
		} else if (stringListener != null) {
			new ValueRequest(method).requestString(player, currentValue, stringOptions, allowCustomOption,
					stringListener);
		} else if (booleanListener != null) {
			new ValueRequest(method).requestBoolean(player, booleanListener);
		}
	}

}
