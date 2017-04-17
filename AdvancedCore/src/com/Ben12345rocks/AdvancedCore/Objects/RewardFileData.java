package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

/**
 * The Class RewardFileData.
 */
public class RewardFileData {

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/** The reward. */
	private Reward reward;

	/** The d file. */
	private File dFile;

	/** The data. */
	private FileConfiguration data;

	/**
	 * Instantiates a new reward file data.
	 *
	 * @param reward
	 *            the reward
	 * @param rewardFolder
	 *            the reward folder
	 */
	public RewardFileData(Reward reward, File rewardFolder) {
		this.reward = reward;
		dFile = reward.getFile();

		if (!rewardFolder.isDirectory()) {
			rewardFolder = rewardFolder.getParentFile();
		}

		setup();
	}

	/**
	 * Gets the action bar delay.
	 *
	 * @return the action bar delay
	 */
	public int getActionBarDelay() {
		return getData().getInt("ActionBar.Delay");
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascripts() {
		return (ArrayList<String>) getData().getList("Javascripts",new ArrayList<String>());
	}

	/**
	 * Gets the action bar message.
	 *
	 * @return the action bar message
	 */
	public String getActionBarMessage() {
		return getData().getString("ActionBar.Message");
	}

	/**
	 * Gets the boss bar color.
	 *
	 * @return the boss bar color
	 */
	public String getBossBarColor() {
		return getData().getString("BossBar.Color");
	}

	/**
	 * Gets the boss bar delay.
	 *
	 * @return the boss bar delay
	 */
	public int getBossBarDelay() {
		return getData().getInt("BossBar.Delay");
	}

	/**
	 * Gets the boss bar enabled.
	 *
	 * @return the boss bar enabled
	 */
	public boolean getBossBarEnabled() {
		return getData().getBoolean("BossBar.Enabled");
	}

	/**
	 * Gets the boss bar message.
	 *
	 * @return the boss bar message
	 */
	public String getBossBarMessage() {
		return getData().getString("BossBar.Message");
	}

	/**
	 * Gets the boss bar progress.
	 *
	 * @return the boss bar progress
	 */
	public double getBossBarProgress() {
		return getData().getDouble("BossBar.Progress");
	}

	/**
	 * Gets the boss bar style.
	 *
	 * @return the boss bar style
	 */
	public String getBossBarStyle() {
		return getData().getString("BossBar.Style");
	}

	/**
	 * Gets the chance.
	 *
	 * @return the chance
	 */
	public double getChance() {
		return getData().getDouble("Chance");
	}

	/**
	 * Gets the choice rewards enabled.
	 *
	 * @return the choice rewards enabled
	 */
	public boolean getChoiceRewardsEnabled() {
		return getData().getBoolean("ChoiceRewards.Enabled");
	}

	/**
	 * Gets the choice rewards rewards.
	 *
	 * @return the choice rewards rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getChoiceRewardsRewards() {
		return (ArrayList<String>) getData().getList("ChoiceRewards.Rewards", new ArrayList<String>());
	}

	/**
	 * Gets the commands console.
	 *
	 * @return the commands console
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsConsole() {

		return (ArrayList<String>) getData().getList("Commands.Console", new ArrayList<String>());

	}

	/**
	 * Gets the commands player.
	 *
	 * @return the commands player
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsPlayer() {

		return (ArrayList<String>) getData().getList("Commands.Player", new ArrayList<String>());

	}

	/**
	 * Gets the data.
	 *
	 * @return the data
	 */
	public FileConfiguration getData() {
		return data;
	}

	/**
	 * Gets the delayed enabled.
	 *
	 * @return the delayed enabled
	 */
	public boolean getDelayedEnabled() {
		return getData().getBoolean("Delayed.Enabled");
	}

	/**
	 * Gets the delayed hours.
	 *
	 * @return the delayed hours
	 */
	public int getDelayedHours() {
		return getData().getInt("Delayed.Hours");
	}

	/**
	 * Gets the delayed minutes.
	 *
	 * @return the delayed minutes
	 */
	public int getDelayedMinutes() {
		return getData().getInt("Delayed.Minutes");
	}
	
	public int getDelayedSeconds() {
		return getData().getInt("Delayed.Seconds");
	}

	/**
	 * Gets the effect data.
	 *
	 * @return the effect data
	 */
	public int getEffectData() {
		return getData().getInt("Effect.Data");
	}

	/**
	 * Gets the effect effect.
	 *
	 * @return the effect effect
	 */
	public String getEffectEffect() {
		return getData().getString("Effect.Effect", "");

	}

	/**
	 * Gets the effect enabled.
	 *
	 * @return the effect enabled
	 */
	public boolean getEffectEnabled() {
		return getData().getBoolean("Effect.Enabled");
	}

	/**
	 * Gets the effect particles.
	 *
	 * @return the effect particles
	 */
	public int getEffectParticles() {
		return getData().getInt("Effect.Particles");
	}

	/**
	 * Gets the effect radius.
	 *
	 * @return the effect radius
	 */
	public int getEffectRadius() {
		return getData().getInt("Effect.Radius");
	}

	/**
	 * Gets the exp.
	 *
	 * @return the exp
	 */
	public int getEXP() {
		return getData().getInt("EXP");
	}

	/**
	 * Gets the firework colors.
	 *
	 * @return the firework colors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColors() {
		return (ArrayList<String>) getData().getList("Firework.Colors", new ArrayList<String>());
	}

	/**
	 * Gets the firework colors fade out.
	 *
	 * @return the firework colors fade out
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColorsFadeOut() {
		return (ArrayList<String>) getData().getList("Firework.FadeOutColor", new ArrayList<String>());
	}

	/**
	 * Gets the firework enabled.
	 *
	 * @return the firework enabled
	 */
	public boolean getFireworkEnabled() {
		return getData().getBoolean("Firework.Enabled");
	}

	/**
	 * Gets the firework flicker.
	 *
	 * @return the firework flicker
	 */
	public boolean getFireworkFlicker() {
		return getData().getBoolean("Firework.Flicker");
	}

	/**
	 * Gets the firework power.
	 *
	 * @return the firework power
	 */
	public int getFireworkPower() {
		return getData().getInt("Firework.Power");
	}

	/**
	 * Gets the firework trail.
	 *
	 * @return the firework trail
	 */
	public boolean getFireworkTrail() {
		return getData().getBoolean("Firework.Trail");
	}

	/**
	 * Gets the firework types.
	 *
	 * @return the firework types
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkTypes() {
		return (ArrayList<String>) getData().getList("Firework.Types", new ArrayList<String>());
	}

	/**
	 * Gets the item amount.
	 *
	 * @param item
	 *            the item
	 * @return the item amount
	 */
	@Deprecated
	public int getItemAmount(String item) {
		return getData().getInt("Items." + item + ".Amount");
	}

	/**
	 * Gets the item data.
	 *
	 * @param item
	 *            the item
	 * @return the item data
	 */
	@Deprecated
	public int getItemData(String item) {
		return getData().getInt("Items." + item + ".Data");
	}

	/**
	 * Gets the item durability.
	 *
	 * @param item
	 *            the item
	 * @return the item durability
	 */
	@Deprecated
	public int getItemDurability(String item) {
		return getData().getInt("Items." + item + ".Durability");
	}

	/**
	 * Gets the item enchants.
	 *
	 * @param item
	 *            the item
	 * @return the item enchants
	 */
	@Deprecated
	public Set<String> getItemEnchants(String item) {
		try {
			return getData().getConfigurationSection("Items." + item + ".Enchants").getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	/**
	 * Gets the item enchants level.
	 *
	 * @param item
	 *            the item
	 * @param enchant
	 *            the enchant
	 * @return the item enchants level
	 */
	@Deprecated
	public int getItemEnchantsLevel(String item, String enchant) {
		return getData().getInt("Items." + item + ".Enchants." + enchant);
	}

	/**
	 * Gets the item lore.
	 *
	 * @param item
	 *            the item
	 * @return the item lore
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	public ArrayList<String> getItemLore(String item) {
		return (ArrayList<String>) getData().getList("Items." + item + ".Lore");
	}

	/**
	 * Gets the item material.
	 *
	 * @param item
	 *            the item
	 * @return the item material
	 */
	@Deprecated
	public String getItemMaterial(String item) {
		return getData().getString("Items." + item + ".Material");
	}

	/**
	 * Gets the item max amount.
	 *
	 * @param item
	 *            the item
	 * @return the item max amount
	 */
	@Deprecated
	public int getItemMaxAmount(String item) {
		return getData().getInt("Items." + item + ".MaxAmount");
	}

	/**
	 * Gets the item min amount.
	 *
	 * @param item
	 *            the item
	 * @return the item min amount
	 */
	@Deprecated
	public int getItemMinAmount(String item) {
		return getData().getInt("Items." + item + ".MinAmount");
	}

	/**
	 * Gets the item name.
	 *
	 * @param item
	 *            the item
	 * @return the item name
	 */
	@Deprecated
	public String getItemName(String item) {
		return getData().getString("Items." + item + ".Name");
	}

	/**
	 * Gets the items.
	 *
	 * @return the items
	 */
	public Set<String> getItems() {
		try {
			return getData().getConfigurationSection("Items").getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	public ConfigurationSection getItemSection(String item) {
		return getData().getConfigurationSection("Items." + item);
	}

	/**
	 * Gets the item skull.
	 *
	 * @param item
	 *            the item
	 * @return the item skull
	 */
	@Deprecated
	public String getItemSkull(String item) {
		return getData().getString("Items." + item + ".Skull");
	}

	/**
	 * Gets the javascript enabled.
	 *
	 * @return the javascript enabled
	 */
	public boolean getJavascriptEnabled() {
		return getData().getBoolean("Javascript.Enabled");
	}

	/**
	 * Gets the javascript expression.
	 *
	 * @return the javascript expression
	 */
	public String getJavascriptExpression() {
		return getData().getString("Javascript.Expression", "");
	}

	public String getJavascriptFalseRewardsPath() {
		return "Javascript.FalseRewards";
	}

	public String getJavascriptTrueRewardsPath() {
		return "Javascript.TrueRewards";
	}

	/**
	 * Gets the max exp.
	 *
	 * @return the max exp
	 */
	public int getMaxExp() {
		return getData().getInt("MaxEXP");
	}

	/**
	 * Gets the max money.
	 *
	 * @return the max money
	 */
	public int getMaxMoney() {
		return getData().getInt("MaxMoney");
	}

	/**
	 * Gets the messages broadcast.
	 *
	 * @return the messages broadcast
	 */
	public String getMessagesBroadcast() {
		return getData().getString("Messages.Broadcast", "");
	}

	/**
	 * Gets the messages reward.
	 *
	 * @return the messages reward
	 */
	public String getMessagesPlayer() {
		return getData().getString("Messages.Player", getData().getString("Messages.Reward", ""));
	}

	/**
	 * Gets the min exp.
	 *
	 * @return the min exp
	 */
	public int getMinExp() {
		return getData().getInt("MinEXP");
	}

	/**
	 * Gets the min money.
	 *
	 * @return the min money
	 */
	public int getMinMoney() {
		return getData().getInt("MinMoney");
	}

	/**
	 * Gets the money.
	 *
	 * @return the money
	 */
	public int getMoney() {
		return getData().getInt("Money");
	}

	/**
	 * Gets the permission.
	 *
	 * @return the permission
	 */
	public String getPermission() {
		return getData().getString("Permission", "AdvancedCore.Reward." + reward);
	}

	/**
	 * Gets the potions.
	 *
	 * @return the potions
	 */
	public Set<String> getPotions() {
		try {
			return getData().getConfigurationSection("Potions").getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	/**
	 * Gets the potions amplifier.
	 *
	 * @param potion
	 *            the potion
	 * @return the potions amplifier
	 */
	public int getPotionsAmplifier(String potion) {
		return getData().getInt("Potions." + potion + ".Amplifier");
	}

	/**
	 * Gets the potions duration.
	 *
	 * @param potion
	 *            the potion
	 * @return the potions duration
	 */
	public int getPotionsDuration(String potion) {
		return getData().getInt("Potions." + potion + ".Duration");
	}

	/**
	 * Gets the random chance.
	 *
	 * @return the random chance
	 */
	public double getRandomChance() {
		return getData().getDouble("Random.Chance");
	}

	public String getRandomFallBackRewardsPath() {
		return "Random.FallBack";
	}

	public boolean getRandomPickRandom() {
		return getData().getBoolean("Random.PickRandom", true);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomRewards() {
		return (ArrayList<String>) getData().getList("Random.Rewards", new ArrayList<String>());
	}

	public String getRandomRewardsPath() {
		return "Random.Rewards";
	}

	/**
	 * Gets the require permission.
	 *
	 * @return the require permission
	 */
	public boolean getRequirePermission() {
		return getData().getBoolean("RequirePermission");
	}

	/**
	 * Gets the reward type.
	 *
	 * @return the reward type
	 */
	public String getRewardType() {
		String str = getData().getString("RewardType", "BOTH");
		if (str != null) {
			if (str.equalsIgnoreCase("online")) {
				return "ONLINE";
			} else if (str.equalsIgnoreCase("offline")) {
				return "OFFLINE";
			} else {
				return "BOTH";
			}
		}
		return "BOTH";
	}

	/**
	 * Gets the sound enabled.
	 *
	 * @return the sound enabled
	 */
	public boolean getSoundEnabled() {
		return getData().getBoolean("Sound.Enabled");
	}

	/**
	 * Gets the sound pitch.
	 *
	 * @return the sound pitch
	 */
	public float getSoundPitch() {
		return (float) getData().getDouble("Sound.Pitch");
	}

	/**
	 * Gets the sound sound.
	 *
	 * @return the sound sound
	 */
	public String getSoundSound() {
		return getData().getString("Sound.Sound");
	}

	/**
	 * Gets the sound volume.
	 *
	 * @return the sound volume
	 */
	public float getSoundVolume() {
		return (float) getData().getDouble("Sound.Volume");
	}

	/**
	 * Gets the timed enabled.
	 *
	 * @return the timed enabled
	 */
	public boolean getTimedEnabled() {
		return getData().getBoolean("Timed.Enabled");
	}

	/**
	 * Gets the timed hour.
	 *
	 * @return the timed hour
	 */
	public int getTimedHour() {
		return getData().getInt("Timed.Hour");
	}

	/**
	 * Gets the timed minute.
	 *
	 * @return the timed minute
	 */
	public int getTimedMinute() {
		return getData().getInt("Timed.Minute");
	}

	/**
	 * Gets the title enabled.
	 *
	 * @return the title enabled
	 */
	public boolean getTitleEnabled() {
		return getData().getBoolean("Title.Enabled");
	}

	/**
	 * Gets the title fade in.
	 *
	 * @return the title fade in
	 */
	public int getTitleFadeIn() {
		return getData().getInt("Title.FadeIn");
	}

	/**
	 * Gets the title fade out.
	 *
	 * @return the title fade out
	 */
	public int getTitleFadeOut() {
		return getData().getInt("Title.FadeOut");
	}

	/**
	 * Gets the title show time.
	 *
	 * @return the title show time
	 */
	public int getTitleShowTime() {
		return getData().getInt("Title.ShowTime");
	}

	/**
	 * Gets the title sub title.
	 *
	 * @return the title sub title
	 */
	public String getTitleSubTitle() {
		return getData().getString("Title.SubTitle");
	}

	/**
	 * Gets the title title.
	 *
	 * @return the title title
	 */
	public String getTitleTitle() {
		return getData().getString("Title.Title");
	}

	/**
	 * Gets the worlds.
	 *
	 * @return the worlds
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getWorlds() {

		return (ArrayList<String>) getData().getList("Worlds", new ArrayList<String>());

	}

	/**
	 * Reload.
	 */
	public void reload() {
		data = YamlConfiguration.loadConfiguration(dFile);
	}

	/**
	 * Sets the.
	 *
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public void set(String path, Object value) {
		data.set(path, value);
		FilesManager.getInstance().editFile(dFile, data);
		reload();
	}

	/**
	 * Sets the chance.
	 *
	 * @param d
	 *            the new chance
	 */
	public void setChance(double d) {
		set("Chance", d);
	}

	/**
	 * Sets the commands console.
	 *
	 * @param value
	 *            the new commands console
	 */
	public void setCommandsConsole(ArrayList<String> value) {
		set("Commands.Console", value);
	}

	/**
	 * Sets the commands player.
	 *
	 * @param value
	 *            the new commands player
	 */
	public void setCommandsPlayer(ArrayList<String> value) {
		set("Commands.Player", value);
	}

	public void setData(ConfigurationSection value) {
		Map<String, Object> map = value.getConfigurationSection("").getValues(true);
		for (Entry<String, Object> entry : map.entrySet()) {
			set(entry.getKey(), entry.getValue());
		}
		reward.loadValues();
	}

	/**
	 * Sets the exp.
	 *
	 * @param value
	 *            the new exp
	 */
	public void setEXP(int value) {
		set("EXP", value);
	}

	/**
	 * Sets the give in each world.
	 *
	 * @param value
	 *            the new give in each world
	 */
	public void setGiveInEachWorld(boolean value) {
		set("GiveInEachWorld", value);
	}

	/**
	 * Sets the item amount.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemAmount(String item, int value) {
		set("Items." + item + ".Amount", value);
	}

	/**
	 * Sets the item data.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemData(String item, int value) {
		set("Items." + item + ".Data", value);
	}

	/**
	 * Sets the item durability.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemDurability(String item, int value) {
		set("Items." + item + ".Durability", value);
	}

	/**
	 * Sets the item enchant.
	 *
	 * @param item
	 *            the item
	 * @param enchant
	 *            the enchant
	 * @param value
	 *            the value
	 */
	public void setItemEnchant(String item, String enchant, int value) {
		set("Items." + item + ".Enchants." + enchant, value);
	}

	/**
	 * Sets the item lore.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemLore(String item, ArrayList<String> value) {
		set("Items." + item + ".Lore", value);
	}

	/**
	 * Sets the item material.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMaterial(String item, String value) {
		set("Items." + item + ".Material", value);
	}

	/**
	 * Sets the item max amount.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMaxAmount(String item, int value) {
		set("Items." + item + ".MaxAmount", value);
	}

	/**
	 * Sets the item min amount.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMinAmount(String item, int value) {
		set("Items." + item + ".MinAmount", value);
	}

	/**
	 * Sets the item name.
	 *
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemName(String item, String value) {
		set("Items." + item + ".Name", value);
	}

	/**
	 * Sets the max exp.
	 *
	 * @param value
	 *            the new max exp
	 */
	public void setMaxExp(int value) {
		set("MaxEXP", value);
	}

	/**
	 * Sets the max money.
	 *
	 * @param value
	 *            the new max money
	 */
	public void setMaxMoney(int value) {
		set("MaxMoney", value);
	}

	/**
	 * Sets the messages broadcast.
	 *
	 * @param value
	 *            the new messages broadcast
	 */
	public void setMessagesBroadcast(String value) {
		set("Messages.Broadcast", value);
	}

	/**
	 * Sets the messages reward.
	 *
	 * @param value
	 *            the new messages reward
	 */
	public void setMessagesPlayer(String value) {
		set("Messages.Player", value);
	}

	/**
	 * Sets the min exp.
	 *
	 * @param value
	 *            the new min exp
	 */
	public void setMinExp(int value) {
		set("MinEXP", value);
	}

	/**
	 * Sets the min money.
	 *
	 * @param value
	 *            the new min money
	 */
	public void setMinMoney(int value) {
		set("MinMoney", value);
	}

	/**
	 * Sets the money.
	 *
	 * @param value
	 *            the new money
	 */
	public void setMoney(int value) {
		set("Money", value);
	}

	/**
	 * Sets the permission.
	 *
	 * @param perm
	 *            the new permission
	 */
	public void setPermission(String perm) {
		set("Permission", perm);
	}

	/**
	 * Sets the potions amplifier.
	 *
	 * @param potion
	 *            the potion
	 * @param value
	 *            the value
	 */
	public void setPotionsAmplifier(String potion, int value) {
		set("Potions." + potion + ".Amplifier", value);
	}

	/**
	 * Sets the potions duration.
	 *
	 * @param potion
	 *            the potion
	 * @param value
	 *            the value
	 */
	public void setPotionsDuration(String potion, int value) {
		set("Potions." + potion + ".Duration", value);
	}

	/**
	 * Sets the require permission.
	 *
	 * @param value
	 *            the new require permission
	 */
	public void setRequirePermission(boolean value) {
		set("RequirePermission", value);
	}

	/**
	 * Sets the reward type.
	 *
	 * @param value
	 *            the new reward type
	 */
	public void setRewardType(String value) {
		set("RewardType", value);
	}

	/**
	 * Setup.
	 */
	public void setup() {
		data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			try {
				data.save(dFile);
			} catch (IOException e) {
				plugin.getPlugin().getLogger().severe(ChatColor.RED + "Could not create " + dFile.getAbsolutePath());

			}
		}
	}

	/**
	 * Sets the worlds.
	 *
	 * @param value
	 *            the new worlds
	 */
	public void setWorlds(ArrayList<String> value) {
		set("Worlds", value);
	}
}
