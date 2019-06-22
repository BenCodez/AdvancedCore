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

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

/**
 * The Class RewardFileData.
 */
public class RewardFileData {

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The reward. */
	private Reward reward;

	/** The data file. */
	private File dataFile;

	/** The data. */
	private FileConfiguration fileData;

	private ConfigurationSection configData;

	public RewardFileData(Reward reward, ConfigurationSection section) {
		this.reward = reward;
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
		dataFile = reward.getFile();

		if (!rewardFolder.isDirectory()) {
			rewardFolder = rewardFolder.getParentFile();
		}

		setup();
	}

	/**
	 * Gets the chance.
	 *
	 * @return the chance
	 */
	public double getChance() {
		return getConfigData().getDouble("Chance");
	}

	public Set<String> getChoices() {
		if (getConfigData().isConfigurationSection("Choices")) {
			return getConfigData().getConfigurationSection("Choices").getKeys(false);
		}
		return new HashSet<String>();
	}

	public ConfigurationSection getChoicesItem(String choice) {
		return getConfigData().getConfigurationSection("Choices." + choice + ".DisplayItem");
	}

	public String getChoicesRewardsPath(String choice) {
		return "Choices." + choice + ".Rewards";
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
	 * @return the dFile
	 */
	public File getDataFile() {
		return dataFile;
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

	public ConfigurationSection getDisplayItem() {
		return getConfigData().getConfigurationSection("DisplayItem");
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
	 * Gets the choice rewards enabled.
	 *
	 * @return the choice rewards enabled
	 */
	public boolean getEnableChoices() {
		return getConfigData().getBoolean("EnableChoices");
	}

	public FileConfiguration getFileData() {
		return fileData;
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
	 * Gets the permission.
	 *
	 * @return the permission
	 */
	public String getPermission() {
		return getConfigData().getString("Permission", "AdvancedCore.Reward." + reward.getRewardName());
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getPriority() {
		return (ArrayList<String>) getConfigData().getList("Priority", new ArrayList<String>());
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
	 * Gets the worlds.
	 *
	 * @return the worlds
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getWorlds() {

		return (ArrayList<String>) getConfigData().getList("Worlds", new ArrayList<String>());

	}

	public boolean hasRewardFile() {
		return dataFile != null;
	}

	public boolean isDirectlyDefinedReward() {
		return getConfigData().getBoolean("DirectlyDefinedReward");
	}

	public boolean isRewardFile() {
		return dataFile != null && !isDirectlyDefinedReward();
	}

	/**
	 * Reload.
	 */
	public void reload() {
		fileData = YamlConfiguration.loadConfiguration(dataFile);
		configData = fileData.getConfigurationSection("");
	}

	public void save(FileConfiguration fileData) {
		FilesManager.getInstance().editFile(dataFile, fileData);
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
			save(fileData);
			reload();
		} else {
			plugin.debug("Editing invalid reward: " + reward.getName());
		}
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

	/**
	 * @param dFile
	 *            the dFile to set
	 */
	public void setDataFile(File dFile) {
		this.dataFile = dFile;
	}

	public void setDirectlyDefinedReward(boolean b) {
		set("DirectlyDefinedReward", b);
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
	 * Sets the permission.
	 *
	 * @param perm
	 *            the new permission
	 */
	public void setPermission(String perm) {
		set("Permission", perm);
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
		fileData = YamlConfiguration.loadConfiguration(dataFile);

		if (!dataFile.exists()) {
			try {
				fileData.save(dataFile);
			} catch (IOException e) {
				plugin.getLogger().severe(ChatColor.RED + "Could not create " + dataFile.getAbsolutePath());

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
