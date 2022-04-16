package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;

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
import com.bencodez.advancedcore.api.valuerequest.InputMethod;

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

	public abstract void open(Player player, RewardEditData reward);

	public void openSubReward(Player player, String path, RewardEditData reward) {
		AdvancedCorePlugin.getInstance().getRewardHandler().openSubReward(player, path, reward);
	}

	public void reloadAdvancedCore() {
		AdvancedCorePlugin.getInstance().reloadAdvancedCore(false);
	}

	public abstract void setVal(String key, Object value);

}
