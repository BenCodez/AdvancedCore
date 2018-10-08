package com.Ben12345rocks.AdvancedCore.Rewards;

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
	private FileConfiguration fileData;

	private ConfigurationSection configData;

	public RewardFileData(ConfigurationSection section) {
		configData = section;
	}

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
		return getConfigData().getInt("ActionBar.Delay");
	}

	/**
	 * Gets the action bar message.
	 *
	 * @return the action bar message
	 */
	public String getActionBarMessage() {
		return getConfigData().getString("ActionBar.Message");
	}

	/**
	 * Gets the boss bar color.
	 *
	 * @return the boss bar color
	 */
	public String getBossBarColor() {
		return getConfigData().getString("BossBar.Color");
	}

	/**
	 * Gets the boss bar delay.
	 *
	 * @return the boss bar delay
	 */
	public int getBossBarDelay() {
		return getConfigData().getInt("BossBar.Delay");
	}

	public boolean getBossBarEnabled() {
		return getConfigData().getBoolean("BossBar.Enabled");
	}

	/**
	 * Gets the boss bar message.
	 *
	 * @return the boss bar message
	 */
	public String getBossBarMessage() {
		return getConfigData().getString("BossBar.Message", "");
	}

	/**
	 * Gets the boss bar progress.
	 *
	 * @return the boss bar progress
	 */
	public double getBossBarProgress() {
		return getConfigData().getDouble("BossBar.Progress");
	}

	/**
	 * Gets the boss bar style.
	 *
	 * @return the boss bar style
	 */
	public String getBossBarStyle() {
		return getConfigData().getString("BossBar.Style");
	}

	/**
	 * Gets the chance.
	 *
	 * @return the chance
	 */
	public double getChance() {
		return getConfigData().getDouble("Chance");
	}

	/**
	 * Gets the choice rewards enabled.
	 *
	 * @return the choice rewards enabled
	 */
	public boolean getChoiceRewardsEnabled() {
		return getConfigData().getBoolean("ChoiceRewards.Enabled");
	}

	/**
	 * Gets the choice rewards rewards.
	 *
	 * @return the choice rewards rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getChoiceRewardsRewards() {
		return (ArrayList<String>) getConfigData().getList("ChoiceRewards.Rewards", new ArrayList<String>());
	}

	/**
	 * Gets the commands console.
	 *
	 * @return the commands console
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsConsole() {
		if (getConfigData().isList("Commands")) {
			return (ArrayList<String>) getConfigData().getList("Commands", new ArrayList<String>());
		} else {
			return (ArrayList<String>) getConfigData().getList("Commands.Console", new ArrayList<String>());
		}

	}

	/**
	 * Gets the commands player.
	 *
	 * @return the commands player
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsPlayer() {
		return (ArrayList<String>) getConfigData().getList("Commands.Player", new ArrayList<String>());
	}

	public ConfigurationSection getConfigData() {
		return configData;
	}

	/**
	 * Gets the delayed enabled.
	 *
	 * @return the delayed enabled
	 */
	public boolean getDelayedEnabled() {
		return getConfigData().getBoolean("Delayed.Enabled");
	}

	/**
	 * Gets the delayed hours.
	 *
	 * @return the delayed hours
	 */
	public int getDelayedHours() {
		return getConfigData().getInt("Delayed.Hours");
	}

	/**
	 * Gets the delayed minutes.
	 *
	 * @return the delayed minutes
	 */
	public int getDelayedMinutes() {
		return getConfigData().getInt("Delayed.Minutes");
	}

	public int getDelayedSeconds() {
		return getConfigData().getInt("Delayed.Seconds");
	}

	/**
	 * Gets the effect data.
	 *
	 * @return the effect data
	 */
	public int getEffectData() {
		return getConfigData().getInt("Effect.Data");
	}

	/**
	 * Gets the effect effect.
	 *
	 * @return the effect effect
	 */
	public String getEffectEffect() {
		return getConfigData().getString("Effect.Effect", "");

	}

	/**
	 * Gets the effect enabled.
	 *
	 * @return the effect enabled
	 */
	public boolean getEffectEnabled() {
		return getConfigData().getBoolean("Effect.Enabled");
	}

	/**
	 * Gets the data.
	 *
	 * @return the data
	 */
	/*
	 * private FileConfiguration getFileData() { return fileData; }
	 */

	/**
	 * Gets the effect particles.
	 *
	 * @return the effect particles
	 */
	public int getEffectParticles() {
		return getConfigData().getInt("Effect.Particles");
	}

	/**
	 * Gets the effect radius.
	 *
	 * @return the effect radius
	 */
	public int getEffectRadius() {
		return getConfigData().getInt("Effect.Radius");
	}

	/**
	 * Gets the exp.
	 *
	 * @return the exp
	 */
	public int getEXP() {
		return getConfigData().getInt("EXP");
	}

	public FileConfiguration getFileData() {
		return fileData;
	}

	/**
	 * Gets the firework colors.
	 *
	 * @return the firework colors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColors() {
		return (ArrayList<String>) getConfigData().getList("Firework.Colors", new ArrayList<String>());
	}

	/**
	 * Gets the firework colors fade out.
	 *
	 * @return the firework colors fade out
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColorsFadeOut() {
		return (ArrayList<String>) getConfigData().getList("Firework.FadeOutColor", new ArrayList<String>());
	}

	/**
	 * Gets the firework enabled.
	 *
	 * @return the firework enabled
	 */
	public boolean getFireworkEnabled() {
		return getConfigData().getBoolean("Firework.Enabled");
	}

	/**
	 * Gets the firework flicker.
	 *
	 * @return the firework flicker
	 */
	public boolean getFireworkFlicker() {
		return getConfigData().getBoolean("Firework.Flicker");
	}

	/**
	 * Gets the firework power.
	 *
	 * @return the firework power
	 */
	public int getFireworkPower() {
		return getConfigData().getInt("Firework.Power");
	}

	/**
	 * Gets the firework trail.
	 *
	 * @return the firework trail
	 */
	public boolean getFireworkTrail() {
		return getConfigData().getBoolean("Firework.Trail");
	}

	/**
	 * Gets the firework types.
	 *
	 * @return the firework types
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkTypes() {
		return (ArrayList<String>) getConfigData().getList("Firework.Types", new ArrayList<String>());
	}

	public boolean getForceOffline() {
		return getConfigData().getBoolean("ForceOffline");
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
		return getConfigData().getInt("Items." + item + ".Amount");
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
		return getConfigData().getInt("Items." + item + ".Data");
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
		return getConfigData().getInt("Items." + item + ".Durability");
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
			return getConfigData().getConfigurationSection("Items." + item + ".Enchants").getKeys(false);
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
		return getConfigData().getInt("Items." + item + ".Enchants." + enchant);
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
		return (ArrayList<String>) getConfigData().getList("Items." + item + ".Lore");
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
		return getConfigData().getString("Items." + item + ".Material");
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
		return getConfigData().getInt("Items." + item + ".MaxAmount");
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
		return getConfigData().getInt("Items." + item + ".MinAmount");
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
		return getConfigData().getString("Items." + item + ".Name");
	}

	/**
	 * Gets the items.
	 *
	 * @return the items
	 */
	public Set<String> getItems() {
		try {
			return getConfigData().getConfigurationSection("Items").getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	public ConfigurationSection getItemSection(String item) {
		return getConfigData().getConfigurationSection("Items." + item);
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
		return getConfigData().getString("Items." + item + ".Skull");
	}

	/**
	 * Gets the javascript enabled.
	 *
	 * @return the javascript enabled
	 */
	public boolean getJavascriptEnabled() {
		return getConfigData().getBoolean("Javascript.Enabled");
	}

	/**
	 * Gets the javascript expression.
	 *
	 * @return the javascript expression
	 */
	public String getJavascriptExpression() {
		return getConfigData().getString("Javascript.Expression", "");
	}

	public String getJavascriptFalseRewardsPath() {
		return "Javascript.FalseRewards";
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascripts() {
		return (ArrayList<String>) getConfigData().getList("Javascripts", new ArrayList<String>());
	}

	public String getJavascriptTrueRewardsPath() {
		return "Javascript.TrueRewards";
	}

	public Set<String> getLuckyRewards() {
		if (getConfigData().getConfigurationSection("Lucky") != null) {
			return getConfigData().getConfigurationSection("Lucky").getKeys(false);
		} else {
			return new HashSet<String>();
		}
	}

	public String getLuckyRewardsPath(int num) {
		return "Lucky." + num;
	}

	/**
	 * Gets the max exp.
	 *
	 * @return the max exp
	 */
	public int getMaxExp() {
		return getConfigData().getInt("MaxEXP");
	}

	/**
	 * Gets the max money.
	 *
	 * @return the max money
	 */
	public int getMaxMoney() {
		return getConfigData().getInt("MaxMoney");
	}

	/**
	 * Gets the messages broadcast.
	 *
	 * @return the messages broadcast
	 */
	public String getMessagesBroadcast() {
		return getConfigData().getString("Messages.Broadcast", "");
	}

	/**
	 * Gets the messages reward.
	 *
	 * @return the messages reward
	 */
	public String getMessagesPlayer() {
		return getConfigData().getString("Messages.Player", getConfigData().getString("Messages.Reward", ""));
	}

	/**
	 * Gets the min exp.
	 *
	 * @return the min exp
	 */
	public int getMinExp() {
		return getConfigData().getInt("MinEXP");
	}

	/**
	 * Gets the min money.
	 *
	 * @return the min money
	 */
	public int getMinMoney() {
		return getConfigData().getInt("MinMoney");
	}

	/**
	 * Gets the money.
	 *
	 * @return the money
	 */
	public int getMoney() {
		return getConfigData().getInt("Money");
	}

	public boolean getOnlyOneLucky() {
		return getConfigData().getBoolean("OnlyOneLucky");
	}

	/**
	 * Gets the permission.
	 *
	 * @return the permission
	 */
	public String getPermission() {
		return getConfigData().getString("Permission", "AdvancedCore.Reward." + reward);
	}

	/**
	 * Gets the potions.
	 *
	 * @return the potions
	 */
	public Set<String> getPotions() {
		try {
			return getConfigData().getConfigurationSection("Potions").getKeys(false);
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
		return getConfigData().getInt("Potions." + potion + ".Amplifier");
	}

	/**
	 * Gets the potions duration.
	 *
	 * @param potion
	 *            the potion
	 * @return the potions duration
	 */
	public int getPotionsDuration(String potion) {
		return getConfigData().getInt("Potions." + potion + ".Duration");
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getPriority() {
		return (ArrayList<String>) getConfigData().getList("Priority", new ArrayList<String>());
	}

	/**
	 * Gets the random chance.
	 *
	 * @return the random chance
	 */
	public double getRandomChance() {
		return getConfigData().getDouble("Random.Chance");
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomCommand() {
		return (ArrayList<String>) getConfigData().getList("RandomCommand", new ArrayList<String>());
	}

	public String getRandomFallBackRewardsPath() {
		return "Random.FallBack";
	}

	public boolean getRandomPickRandom() {
		return getConfigData().getBoolean("Random.PickRandom", true);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomRewards() {
		return (ArrayList<String>) getConfigData().getList("Random.Rewards", new ArrayList<String>());
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
		return getConfigData().getBoolean("RequirePermission");
	}

	/**
	 * Gets the reward type.
	 *
	 * @return the reward type
	 */
	public String getRewardType() {
		String str = getConfigData().getString("RewardType", "BOTH");
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

	public String getServer() {
		return getConfigData().getString("Server", "");
	}

	/**
	 * Gets the sound enabled.
	 *
	 * @return the sound enabled
	 */
	public boolean getSoundEnabled() {
		return getConfigData().getBoolean("Sound.Enabled");
	}

	/**
	 * Gets the sound pitch.
	 *
	 * @return the sound pitch
	 */
	public float getSoundPitch() {
		return (float) getConfigData().getDouble("Sound.Pitch");
	}

	/**
	 * Gets the sound sound.
	 *
	 * @return the sound sound
	 */
	public String getSoundSound() {
		return getConfigData().getString("Sound.Sound");
	}

	/**
	 * Gets the sound volume.
	 *
	 * @return the sound volume
	 */
	public float getSoundVolume() {
		return (float) getConfigData().getDouble("Sound.Volume");
	}

	/**
	 * Gets the timed enabled.
	 *
	 * @return the timed enabled
	 */
	public boolean getTimedEnabled() {
		return getConfigData().getBoolean("Timed.Enabled");
	}

	/**
	 * Gets the timed hour.
	 *
	 * @return the timed hour
	 */
	public int getTimedHour() {
		return getConfigData().getInt("Timed.Hour");
	}

	/**
	 * Gets the timed minute.
	 *
	 * @return the timed minute
	 */
	public int getTimedMinute() {
		return getConfigData().getInt("Timed.Minute");
	}

	/**
	 * Gets the title enabled.
	 *
	 * @return the title enabled
	 */
	public boolean getTitleEnabled() {
		return getConfigData().getBoolean("Title.Enabled");
	}

	/**
	 * Gets the title fade in.
	 *
	 * @return the title fade in
	 */
	public int getTitleFadeIn() {
		return getConfigData().getInt("Title.FadeIn");
	}

	/**
	 * Gets the title fade out.
	 *
	 * @return the title fade out
	 */
	public int getTitleFadeOut() {
		return getConfigData().getInt("Title.FadeOut");
	}

	/**
	 * Gets the title show time.
	 *
	 * @return the title show time
	 */
	public int getTitleShowTime() {
		return getConfigData().getInt("Title.ShowTime");
	}

	/**
	 * Gets the title sub title.
	 *
	 * @return the title sub title
	 */
	public String getTitleSubTitle() {
		return getConfigData().getString("Title.SubTitle");
	}

	/**
	 * Gets the title title.
	 *
	 * @return the title title
	 */
	public String getTitleTitle() {
		return getConfigData().getString("Title.Title");
	}

	/**
	 * Gets the worlds.
	 *
	 * @return the worlds
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getWorlds() {

		return (ArrayList<String>) getConfigData().getList("Worlds", new ArrayList<String>());

	}

	public boolean isDirectlyDefinedReward() {
		return getConfigData().getBoolean("DirectlyDefinedReward");
	}

	public boolean isRewardFile() {
		return dFile != null;
	}

	/**
	 * Reload.
	 */
	public void reload() {
		fileData = YamlConfiguration.loadConfiguration(dFile);
		configData = fileData.getConfigurationSection("");
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
		if (fileData != null) {
			fileData.set(path, value);
			FilesManager.getInstance().editFile(dFile, fileData);
			reload();
		} else {
			plugin.debug("Editing invalid reward: " + reward.getName());
		}
	}

	public void setActionBarDelay(int value) {
		set("ActionBar.Delay", value);
	}

	public void setActionBarMsg(String value) {
		set("ActionBar.Message", value);
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

	public void setConfigData(ConfigurationSection configData) {
		this.configData = configData;
	}

	/*
	 * public void setData(ConfigurationSection value) { getFileData().set("",
	 * value); reward.loadValues(); }
	 */

	public void setData(ConfigurationSection value) {
		// getFileData().set("", value);
		Map<String, Object> map = value.getConfigurationSection("").getValues(true);
		for (Entry<String, Object> entry : map.entrySet()) {
			set(entry.getKey(), entry.getValue());
		}
		reward.loadValues();
	}

	public void setDirectlyDefinedReward(boolean b) {
		set("DirectlyDefinedReward", b);
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

	public void setJavascripts(ArrayList<String> value) {
		set("Javascripts", value);
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

	public void setPriority(ArrayList<String> value) {
		set("Priority", value);
	}

	public void setRandomCommand(ArrayList<String> value) {
		set("RandomCommand", value);
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
		fileData = YamlConfiguration.loadConfiguration(dFile);

		if (!dFile.exists()) {
			try {
				fileData.save(dFile);
			} catch (IOException e) {
				plugin.getPlugin().getLogger().severe(ChatColor.RED + "Could not create " + dFile.getAbsolutePath());

			}
		}
		configData = fileData.getConfigurationSection("");
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
