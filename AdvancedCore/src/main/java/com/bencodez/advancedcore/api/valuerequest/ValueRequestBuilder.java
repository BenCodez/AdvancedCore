package com.bencodez.advancedcore.api.valuerequest;

import java.util.LinkedHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.valuerequest.listeners.BooleanListener;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;
import com.bencodez.advancedcore.api.valuerequest.listeners.NumberListener;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;

public class ValueRequestBuilder {

	private boolean allowCustomOption = false;
	private BooleanListener booleanListener;
	private String currentValue = "";
	private InputMethod method = null;
	private LinkedHashMap<Number, ItemStack> numberItemOptions;
	private NumberListener numberListener;
	private Number[] numberOptions;
	private LinkedHashMap<String, ItemStack> stringItemOptions;

	private StringListener stringListener;
	private String[] stringOptions;

	public ValueRequestBuilder(BooleanListener listener) {
		booleanListener = listener;
	}

	public ValueRequestBuilder(LinkedHashMap<String, ItemStack> options, final Listener<String> listener) {
		stringListener = new StringListener() {

			@Override
			public void onInput(Player player, String value) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler()
						.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								listener.onInput(player, value);
							}
						});
			}
		};
		stringItemOptions = options;
	}

	public ValueRequestBuilder(final Listener<Boolean> listener) {
		booleanListener = new BooleanListener() {

			@Override
			public void onInput(Player player, boolean value) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler()
						.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								listener.onInput(player, value);
							}
						});
			}
		};
	}

	public ValueRequestBuilder(final Listener<Number> listener, LinkedHashMap<Number, ItemStack> options) {
		numberListener = new NumberListener() {

			@Override
			public void onInput(Player player, Number value) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler()
						.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								listener.onInput(player, value);
							}
						});
			}
		};
		numberItemOptions = options;
	}

	public ValueRequestBuilder(final Listener<Number> listener, Number[] options) {
		numberListener = new NumberListener() {

			@Override
			public void onInput(Player player, Number value) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler()
						.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								listener.onInput(player, value);
							}
						});
			}
		};
		numberOptions = options;
	}

	public ValueRequestBuilder(final Listener<String> listener, String[] options) {
		stringListener = new StringListener() {

			@Override
			public void onInput(Player player, String value) {
				AdvancedCorePlugin.getInstance().getBukkitScheduler()
						.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								listener.onInput(player, value);
							}
						});
			}
		};
		stringOptions = options;
	}

	public ValueRequestBuilder(NumberListener listener, LinkedHashMap<Number, ItemStack> options) {
		numberListener = listener;
		numberItemOptions = options;
	}

	public ValueRequestBuilder(NumberListener listener, Number[] options) {
		numberListener = listener;
		numberOptions = options;
	}

	public ValueRequestBuilder(StringListener listener, LinkedHashMap<String, ItemStack> options) {
		stringListener = listener;
		stringItemOptions = options;
	}

	public ValueRequestBuilder(StringListener listener, String[] options) {
		stringListener = listener;
		stringOptions = options;
	}

	public ValueRequestBuilder allowCustomOption(boolean allowCustomOption) {
		this.allowCustomOption = allowCustomOption;
		return this;
	}

	public ValueRequestBuilder currentValue(String currentValue) {
		this.currentValue = currentValue;
		return this;
	}

	public void request(Player player) {
		if (numberListener != null) {
			if (numberItemOptions == null) {
				new ValueRequest(method).requestNumber(player, currentValue, numberOptions, allowCustomOption,
						numberListener);
			} else {
				new ValueRequest(method).requestNumber(player, numberItemOptions, currentValue, allowCustomOption,
						numberListener);
			}
		} else if (stringListener != null) {
			if (stringItemOptions == null) {
				new ValueRequest(method).requestString(player, currentValue, stringOptions, allowCustomOption,
						stringListener);
			} else {
				new ValueRequest(method).requestString(player, stringItemOptions, currentValue, allowCustomOption,
						stringListener);
			}
		} else if (booleanListener != null) {
			new ValueRequest(method).requestBoolean(player, currentValue, booleanListener);
		}
	}

	public ValueRequestBuilder usingMethod(InputMethod method) {
		if (method != null) {
			this.method = method;
		}
		return this;
	}

}
