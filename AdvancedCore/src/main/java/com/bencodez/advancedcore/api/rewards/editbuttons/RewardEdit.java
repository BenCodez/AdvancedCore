package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.simpleapi.valuerequest.InputMethod;
import com.bencodez.simpleapi.valuerequest.MultiStringListener;
import com.bencodez.simpleapi.valuerequest.NumberListener;
import com.bencodez.simpleapi.valuerequest.StringListener;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

public abstract class RewardEdit {

	public EditGUIButton getBackButton(RewardEditData reward) {
		EditGUIButton b = new EditGUIButton(new ItemBuilder("BARRIER").setName("&cGo back"),
				new EditGUIValueInventory("") {

					@Override
					public void openInventory(ClickEvent clickEvent) {
						reward.reOpenEditGUI(clickEvent.getPlayer());
					}
				});
		b.setSlot(-2);
		return b;
	}

	public EditGUIButton getBackButtonCustom(RewardEditData reward, EditGUIValueInventory edit) {
		EditGUIButton b = new EditGUIButton(new ItemBuilder("BARRIER").setName("&cGo back"), edit);
		b.setSlot(-2);
		return b;
	}

	public EditGUIButton getBooleanButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueBoolean(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, boolean value) {
				setVal(key, value);
				open(player, reward);
			}
		});
	}

	public EditGUIButton getDoubleButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueNumber(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, Number num) {
				setVal(key, num.doubleValue());
				open(player, reward);
			}
		});
	}

	public EditGUIButton getIntButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueNumber(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, Number num) {
				setVal(key, num.intValue());
				open(player, reward);
			}
		});
	}

	public EditGUIButton getStringButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueString(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, String str) {
				setVal(key, str);
				open(player, reward);
			}
		});
	}

	public EditGUIButton getStringButton(String key, RewardEditData reward, String... options) {
		return new EditGUIButton(new EditGUIValueString(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, String str) {
				setVal(key, str);
				open(player, reward);
			}
		}.addOptions(options).inputMethod(InputMethod.INVENTORY));
	}

	public EditGUIButton getStringListButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueList(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, ArrayList<String> value) {
				setVal(key, value);
				open(player, reward);
			}
		});
	}

	/**
	 * Create a new value request using the plugin dialog service.
	 *
	 * @param method the input method
	 * @return the value request
	 */
	public ValueRequest getValueRequest(InputMethod method) {
		return new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
				method);
	}

	/**
	 * Request a string value using the given input method.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param prompt       the prompt
	 * @param listener     the listener
	 */
	public void requestString(Player player, String currentValue, String prompt, StringListener listener) {
		requestString(player, currentValue, prompt, InputMethod.DIALOG, listener);
	}

	/**
	 * Request a string value using the given input method.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param prompt       the prompt
	 * @param method       the method
	 * @param listener     the listener
	 */
	public void requestString(Player player, String currentValue, String prompt, InputMethod method,
			StringListener listener) {
		getValueRequest(method).requestString(player, currentValue, null, true, prompt, listener);
	}

	/**
	 * Request a string value with options.
	 *
	 * @param player            the player
	 * @param currentValue      the current value
	 * @param options           the options
	 * @param allowCustomOption allow custom option
	 * @param prompt            the prompt
	 * @param method            the method
	 * @param listener          the listener
	 */
	public void requestString(Player player, String currentValue, List<String> options, boolean allowCustomOption,
			String prompt, InputMethod method, StringListener listener) {
		getValueRequest(method).requestString(player, currentValue, options, allowCustomOption, prompt, listener);
	}

	/**
	 * Request a number using the given input method.
	 *
	 * @param player       the player
	 * @param currentValue the current value
	 * @param prompt       the prompt
	 * @param listener     the listener
	 */
	public void requestNumber(Player player, Number currentValue, String prompt, NumberListener listener) {
		requestNumber(player, currentValue, null, true, prompt, InputMethod.DIALOG, listener);
	}

	/**
	 * Request a number using the given input method.
	 *
	 * @param player            the player
	 * @param currentValue      the current value
	 * @param options           the options
	 * @param allowCustomOption allow custom option
	 * @param prompt            the prompt
	 * @param method            the method
	 * @param listener          the listener
	 */
	public void requestNumber(Player player, Number currentValue, List<? extends Number> options,
			boolean allowCustomOption, String prompt, InputMethod method, NumberListener listener) {
		getValueRequest(method).requestNumber(player, currentValue, options, allowCustomOption, prompt, listener);
	}

	/**
	 * Request multiple strings at once. Dialog will use multiple fields, other
	 * methods will request one after another.
	 *
	 * @param player        the player
	 * @param prompts       the prompts
	 * @param currentValues the current values
	 * @param listener      the listener
	 */
	public void requestMultipleStrings(Player player, List<String> prompts, List<String> currentValues,
			MultiStringListener listener) {
		getValueRequest(InputMethod.DIALOG).requestMultipleStrings(player, prompts, currentValues, listener);
	}

	public abstract void open(Player player, RewardEditData reward);

	public void openSubReward(Player player, String path, RewardEditData reward) {
		AdvancedCorePlugin.getInstance().getRewardHandler().openSubReward(player, path, reward);
	}

	public void reloadAdvancedCore() {
		AdvancedCorePlugin.getInstance().reloadAdvancedCore(false);
	}

	public abstract void setVal(String key, Object value);

}