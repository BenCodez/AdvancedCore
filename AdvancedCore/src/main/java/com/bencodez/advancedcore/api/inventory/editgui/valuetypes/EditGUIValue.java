package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.valuerequest.InputMethod;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract base class for GUI edit values.
 */
public abstract class EditGUIValue {
	@Getter
	@Setter
	private boolean canGetValue = true;

	@Getter
	@Setter
	private Object currentValue;

	@Getter
	@Setter
	private InputMethod inputMethod = InputMethod.DIALOG;

	@Getter
	@Setter
	private BInventory inv;

	@Getter
	@Setter
	private String key;

	@Getter
	@Setter
	private ArrayList<String> lores;

	@Getter
	private final ArrayList<String> options = new ArrayList<>();

	protected EditGUIValue() {
	}

	protected EditGUIValue(String key, Object currentValue) {
		this.key = key;
		this.currentValue = currentValue;
	}

	public EditGUIValue addLore(ArrayList<String> lore) {
		if (lore == null || lore.isEmpty()) {
			return this;
		}
		if (lores == null) {
			lores = new ArrayList<>();
		}
		lores.addAll(lore);
		return this;
	}

	public EditGUIValue addLore(String lore) {
		if (lore == null) {
			return this;
		}
		if (lores == null) {
			lores = new ArrayList<>();
		}
		lores.add(lore);
		return this;
	}

	public EditGUIValue addOptions(String... values) {
		if (values == null) {
			return this;
		}
		for (String value : values) {
			if (value != null) {
				options.add(value);
			}
		}
		return this;
	}

	public boolean containsKey(RewardEditData rewardEditData) {
		return rewardEditData != null && rewardEditData.hasPath(getKey());
	}

	public EditGUIValue dialog() {
		return inputMethod(InputMethod.DIALOG);
	}

	public EditGUIValue chat() {
		return inputMethod(InputMethod.CHAT);
	}

	public EditGUIValue inventory() {
		return inputMethod(InputMethod.INVENTORY);
	}

	public EditGUIValue book() {
		return inputMethod(InputMethod.BOOK);
	}

	public EditGUIValue sign() {
		return inputMethod(InputMethod.SIGN);
	}

	public abstract String getType();

	/**
	 * Default display name used by {@link com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton}.
	 */
	public String getButtonName() {
		return "&cSet " + getType() + " for " + getKey();
	}

	/**
	 * Default display lore used by {@link com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton}.
	 */
	public List<String> getButtonLore() {
		return List.of("&cCurrent value: " + String.valueOf(getCurrentValue()));
	}

	public EditGUIValue inputMethod(InputMethod inputMethod) {
		this.inputMethod = inputMethod == null ? InputMethod.DIALOG : inputMethod;
		return this;
	}

	public abstract void onClick(ClickEvent event);

	protected ValueRequest createValueRequest() {
		return createValueRequest(getInputMethod());
	}

	protected ValueRequest createValueRequest(InputMethod method) {
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		return new ValueRequest(plugin, plugin.getDialogService(), method == null ? InputMethod.DIALOG : method);
	}

	protected <T> void applyValue(Player player, T value, Consumer<T> saveAction) {
		saveAction.accept(value);
		setCurrentValue(value);
		if (player != null) {
			player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + String.valueOf(value)));
		}
	}

	protected String currentString(String defaultValue) {
		Object current = getCurrentValue();
		return current == null ? defaultValue : String.valueOf(current);
	}

	protected Number currentNumber(Number defaultValue) {
		Object current = getCurrentValue();
		if (current instanceof Number) {
			return (Number) current;
		}
		if (current != null) {
			try {
				return Double.valueOf(String.valueOf(current));
			} catch (NumberFormatException ignored) {
				// Fall through to the supplied default.
			}
		}
		return defaultValue;
	}

	protected ArrayList<String> currentStringList() {
		return toStringList(getCurrentValue());
	}

	protected ArrayList<String> toStringList(Object value) {
		ArrayList<String> values = new ArrayList<>();
		if (value instanceof Collection<?>) {
			for (Object entry : (Collection<?>) value) {
				if (entry != null) {
					values.add(String.valueOf(entry));
				}
			}
		}
		return values;
	}
}
