package com.Ben12345rocks.AdvancedCore.Configs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Objects.Reward;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

// TODO: Auto-generated Javadoc
/**
 * The Class ConfigRewards.
 */
@Deprecated
public class ConfigRewards {

	/** The instance. */
	static ConfigRewards instance = new ConfigRewards();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of ConfigRewards.
	 *
	 * @return single instance of ConfigRewards
	 */
	public static ConfigRewards getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new config rewards.
	 */
	private ConfigRewards() {
	}

	/**
	 * Instantiates a new config rewards.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public ConfigRewards(Main plugin) {
		ConfigRewards.plugin = plugin;
	}

	/**
	 * Copy file.
	 *
	 * @param fileName
	 *            the file name
	 */
	private void copyFile(String fileName) {
		File file = new File(plugin.getDataFolder(), "Rewards" + File.separator
				+ fileName);
		if (!file.exists()) {
			plugin.saveResource("Rewards" + File.separator + fileName, true);
		}
	}

	/**
	 * Gets the action bar delay.
	 *
	 * @param reward
	 *            the reward
	 * @return the action bar delay
	 */
	public int getActionBarDelay(File file, String reward) {
		return getData(file, reward).getInt("ActionBar.Delay");
	}

	/**
	 * Gets the action bar message.
	 *
	 * @param reward
	 *            the reward
	 * @return the action bar message
	 */
	public String getActionBarMessage(File file, String reward) {
		return getData(file, reward).getString("ActionBar.Message");
	}

	/**
	 * Gets the boss bar color.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar color
	 */
	public String getBossBarColor(File file, String reward) {
		return getData(file, reward).getString("BossBar.Color");
	}

	/**
	 * Gets the boss bar delay.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar delay
	 */
	public int getBossBarDelay(File file, String reward) {
		return getData(file, reward).getInt("BossBar.Delay");
	}

	/**
	 * Gets the boss bar enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar enabled
	 */
	public boolean getBossBarEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("BossBar.Enabled");
	}

	/**
	 * Gets the boss bar message.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar message
	 */
	public String getBossBarMessage(File file, String reward) {
		return getData(file, reward).getString("BossBar.Message");
	}

	/**
	 * Gets the boss bar progress.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar progress
	 */
	public double getBossBarProgress(File file, String reward) {
		return getData(file, reward).getDouble("BossBar.Progress");
	}

	/**
	 * Gets the boss bar style.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar style
	 */
	public String getBossBarStyle(File file, String reward) {
		return getData(file, reward).getString("BossBar.Style");
	}

	/**
	 * Gets the chance.
	 *
	 * @param reward
	 *            the reward
	 * @return the chance
	 */
	public double getChance(File file, String reward) {
		return getData(file, reward).getDouble("Chance");
	}

	/**
	 * Gets the choice rewards enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the choice rewards enabled
	 */
	public boolean getChoiceRewardsEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("ChoiceRewards.Enabled");
	}

	/**
	 * Gets the choice rewards rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the choice rewards rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getChoiceRewardsRewards(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"ChoiceRewards.Rewards", new ArrayList<String>());
	}

	/**
	 * Gets the commands console.
	 *
	 * @param reward
	 *            the reward
	 * @return the commands console
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsConsole(File file, String reward) {

		return (ArrayList<String>) getData(file, reward).getList(
				"Commands.Console", new ArrayList<String>());

	}

	/**
	 * Gets the commands player.
	 *
	 * @param reward
	 *            the reward
	 * @return the commands player
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsPlayer(File file, String reward) {

		return (ArrayList<String>) getData(file, reward).getList(
				"Commands.Player", new ArrayList<String>());

	}

	/**
	 * Gets the data.
	 *
	 * @param reward
	 *            the reward
	 * @return the data
	 */
	public FileConfiguration getData(String reward) {
		return getData(new File(plugin.getDataFolder(), "Rewards"), reward);
	}

	public FileConfiguration getData(File file, String reward) {
		File dFile = getRewardFile(file, reward);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		return data;
	}

	/**
	 * Gets the delayed enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed enabled
	 */
	public boolean getDelayedEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Delayed.Enabled");
	}

	/**
	 * Gets the delayed hours.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed hours
	 */
	public int getDelayedHours(File file, String reward) {
		return getData(file, reward).getInt("Delayed.Hours");
	}

	/**
	 * Gets the delayed minutes.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed minutes
	 */
	public int getDelayedMinutes(File file, String reward) {
		return getData(file, reward).getInt("Delayed.Minutes");
	}

	/**
	 * Gets the effect data.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect data
	 */
	public int getEffectData(File file, String reward) {
		return getData(file, reward).getInt("Effect.Data");
	}

	/**
	 * Gets the effect effect.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect effect
	 */
	public String getEffectEffect(File file, String reward) {
		return getData(file, reward).getString("Effect.Effect", "");

	}

	/**
	 * Gets the effect enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect enabled
	 */
	public boolean getEffectEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Effect.Enabled");
	}

	/**
	 * Gets the effect particles.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect particles
	 */
	public int getEffectParticles(File file, String reward) {
		return getData(file, reward).getInt("Effect.Particles");
	}

	/**
	 * Gets the effect radius.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect radius
	 */
	public int getEffectRadius(File file, String reward) {
		return getData(file, reward).getInt("Effect.Radius");
	}

	/**
	 * Gets the exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the exp
	 */
	public int getEXP(File file, String reward) {
		return getData(file, reward).getInt("EXP");
	}

	/**
	 * Gets the firework colors.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework colors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColors(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Firework.Colors", new ArrayList<String>());
	}

	/**
	 * Gets the firework colors fade out.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework colors fade out
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColorsFadeOut(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Firework.FadeOutColor", new ArrayList<String>());
	}

	/**
	 * Gets the firework enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework enabled
	 */
	public boolean getFireworkEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Firework.Enabled");
	}

	/**
	 * Gets the firework flicker.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework flicker
	 */
	public boolean getFireworkFlicker(File file, String reward) {
		return getData(file, reward).getBoolean("Firework.Flicker");
	}

	/**
	 * Gets the firework power.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework power
	 */
	public int getFireworkPower(File file, String reward) {
		return getData(file, reward).getInt("Firework.Power");
	}

	/**
	 * Gets the firework trail.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework trail
	 */
	public boolean getFireworkTrail(File file, String reward) {
		return getData(file, reward).getBoolean("Firework.Trail");
	}

	/**
	 * Gets the firework types.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework types
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkTypes(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Firework.Types", new ArrayList<String>());
	}

	/**
	 * Gets the give in each world.
	 *
	 * @param reward
	 *            the reward
	 * @return the give in each world
	 */
	public boolean getGiveInEachWorld(File file, String reward) {
		return getData(file, reward).getBoolean("GiveInEachWorld");
	}

	/**
	 * Gets the item amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item amount
	 */
	public int getItemAmount(File file, String reward, String item) {
		return getData(file, reward).getInt("Items." + item + ".Amount");
	}

	/**
	 * Gets the item data.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item data
	 */
	public int getItemData(File file, String reward, String item) {
		return getData(file, reward).getInt("Items." + item + ".Data");
	}

	/**
	 * Gets the item durability.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item durability
	 */
	public int getItemDurability(File file, String reward, String item) {
		return getData(file, reward).getInt("Items." + item + ".Durability");
	}

	/**
	 * Gets the item enchants.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item enchants
	 */
	public Set<String> getItemEnchants(File file, String reward, String item) {
		try {
			return getData(file, reward).getConfigurationSection(
					"Items." + item + ".Enchants").getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	/**
	 * Gets the item enchants level.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param enchant
	 *            the enchant
	 * @return the item enchants level
	 */
	public int getItemEnchantsLevel(File file, String reward, String item,
			String enchant) {
		return getData(file, reward).getInt(
				"Items." + item + ".Enchants." + enchant);
	}

	/**
	 * Gets the item lore.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item lore
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getItemLore(File file, String reward, String item) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Items." + item + ".Lore");
	}

	/**
	 * Gets the item material.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item material
	 */
	public String getItemMaterial(File file, String reward, String item) {
		return getData(file, reward).getString("Items." + item + ".Material");
	}

	/**
	 * Gets the item max amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item max amount
	 */
	public int getItemMaxAmount(File file, String reward, String item) {
		return getData(file, reward).getInt("Items." + item + ".MaxAmount");
	}

	/**
	 * Gets the item min amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item min amount
	 */
	public int getItemMinAmount(File file, String reward, String item) {
		return getData(file, reward).getInt("Items." + item + ".MinAmount");
	}

	/**
	 * Gets the item name.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item name
	 */
	public String getItemName(File file, String reward, String item) {
		return getData(file, reward).getString("Items." + item + ".Name");
	}

	/**
	 * Gets the items.
	 *
	 * @param reward
	 *            the reward
	 * @return the items
	 */
	public Set<String> getItems(File file, String reward) {
		try {
			return getData(file, reward).getConfigurationSection("Items")
					.getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	/**
	 * Gets the item skull.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @return the item skull
	 */
	public String getItemSkull(File file, String reward, String item) {
		return getData(file, reward).getString("Items." + item + ".Skull");
	}

	/**
	 * Gets the javascript enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript enabled
	 */
	public boolean getJavascriptEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Javascript.Enabled");
	}

	/**
	 * Gets the javascript expression.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript expression
	 */
	public String getJavascriptExpression(File file, String reward) {
		return getData(file, reward).getString("Javascript.Expression", "");
	}

	/**
	 * Gets the javascript false rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript false rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascriptFalseRewards(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Javascript.FalseRewards", new ArrayList<String>());
	}

	/**
	 * Gets the javascript true rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript true rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascriptTrueRewards(File file, String reward) {
		return (ArrayList<String>) getData(file, reward).getList(
				"Javascript.TrueRewards", new ArrayList<String>());
	}

	/**
	 * Gets the max exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the max exp
	 */
	public int getMaxExp(File file, String reward) {
		return getData(file, reward).getInt("MaxEXP");
	}

	/**
	 * Gets the max money.
	 *
	 * @param reward
	 *            the reward
	 * @return the max money
	 */
	public int getMaxMoney(File file, String reward) {
		return getData(file, reward).getInt("MaxMoney");
	}

	/**
	 * Gets the messages broadcast.
	 *
	 * @param reward
	 *            the reward
	 * @return the messages broadcast
	 */
	public String getMessagesBroadcast(File file, String reward) {
		return getData(file, reward).getString("Messages.Broadcast", "");
	}

	/**
	 * Gets the messages reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the messages reward
	 */
	public String getMessagesReward(File file, String reward) {
		String msg = getData(file, reward).getString("Messages.Reward", "");
		return msg;

	}

	/**
	 * Gets the min exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the min exp
	 */
	public int getMinExp(File file, String reward) {
		return getData(file, reward).getInt("MinEXP");
	}

	/**
	 * Gets the min money.
	 *
	 * @param reward
	 *            the reward
	 * @return the min money
	 */
	public int getMinMoney(File file, String reward) {
		return getData(file, reward).getInt("MinMoney");
	}

	/**
	 * Gets the money.
	 *
	 * @param reward
	 *            the reward
	 * @return the money
	 */
	public int getMoney(File file, String reward) {
		return getData(file, reward).getInt("Money");
	}

	/**
	 * Gets the permission.
	 *
	 * @param reward
	 *            the reward
	 * @return the permission
	 */
	public String getPermission(File file, String reward) {
		return getData(file, reward).getString("Permission",
				"AdvancedCore.Reward." + reward);
	}

	/**
	 * Gets the potions.
	 *
	 * @param reward
	 *            the reward
	 * @return the potions
	 */
	public Set<String> getPotions(File file, String reward) {
		try {
			return getData(file, reward).getConfigurationSection("Potions")
					.getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}

	/**
	 * Gets the potions amplifier.
	 *
	 * @param reward
	 *            the reward
	 * @param potion
	 *            the potion
	 * @return the potions amplifier
	 */
	public int getPotionsAmplifier(File file, String reward, String potion) {
		return getData(file, reward).getInt("Potions." + potion + ".Amplifier");
	}

	/**
	 * Gets the potions duration.
	 *
	 * @param reward
	 *            the reward
	 * @param potion
	 *            the potion
	 * @return the potions duration
	 */
	public int getPotionsDuration(File file, String reward, String potion) {
		return getData(file, reward).getInt("Potions." + potion + ".Duration");
	}

	/**
	 * Gets the random chance.
	 *
	 * @param reward
	 *            the reward
	 * @return the random chance
	 */
	public double getRandomChance(File file, String reward) {
		return getData(file, reward).getDouble("Random.Chance");
	}

	/**
	 * Gets the random fall back.
	 *
	 * @param reward
	 *            the reward
	 * @return the random fall back
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomFallBack(File file, String reward) {
		try {
			return (ArrayList<String>) getData(file, reward).getList(
					"Random.FallBack");
		} catch (Exception ex) {
			return new ArrayList<String>();
		}
	}

	/**
	 * Gets the random rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the random rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomRewards(File file, String reward) {

		return (ArrayList<String>) getData(file, reward).getList(
				"Random.Rewards", new ArrayList<String>());

	}

	/**
	 * Gets the require permission.
	 *
	 * @param reward
	 *            the reward
	 * @return the require permission
	 */
	public boolean getRequirePermission(File file, String reward) {
		return getData(file, reward).getBoolean("RequirePermission");
	}

	/**
	 * Gets the reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the reward
	 */
	@Deprecated
	public Reward getReward(File file, String reward) {
		return RewardHandler.getInstance().getReward(reward);
	}

	/**
	 * Gets the reward file.
	 *
	 * @param reward
	 *            the reward
	 * @return the reward file
	 */
	public File getRewardFile(File file, String reward) {
		File dFile = new File(file, reward + ".yml");
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			try {
				data.save(dFile);
			} catch (IOException e) {
				plugin.getLogger().severe(
						ChatColor.RED + "Could not create Rewards/" + reward
								+ ".yml!");

			}
		}
		return dFile;

	}

	/**
	 * Gets the reward files.
	 *
	 * @return the reward files
	 */
	public ArrayList<String> getRewardFiles(File folder) {
		String[] fileNames = folder.list();
		return Utils.getInstance().convertArray(fileNames);
	}

	@Deprecated
	public ArrayList<String> getRewardNames() {
		return getRewardFiles(new File(plugin.getDataFolder(), "Rewards"));
	}

	/**
	 * Gets the reward names.
	 *
	 * @return the reward names
	 */
	public ArrayList<String> getRewardNames(File file) {
		ArrayList<String> rewardFiles = getRewardFiles(file);
		if (rewardFiles == null) {
			return new ArrayList<String>();
		}
		for (int i = 0; i < rewardFiles.size(); i++) {
			rewardFiles.set(i, rewardFiles.get(i).replace(".yml", ""));
		}

		Collections.sort(rewardFiles, String.CASE_INSENSITIVE_ORDER);

		return rewardFiles;
	}

	/**
	 * Gets the reward type.
	 *
	 * @param reward
	 *            the reward
	 * @return the reward type
	 */
	public String getRewardType(File file, String reward) {
		String str = getData(file, reward).getString("RewardType", "BOTH");
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
	 * @param reward
	 *            the reward
	 * @return the sound enabled
	 */
	public boolean getSoundEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Sound.Enabled");
	}

	/**
	 * Gets the sound pitch.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound pitch
	 */
	public float getSoundPitch(File file, String reward) {
		return (float) getData(file, reward).getDouble("Sound.Pitch");
	}

	/**
	 * Gets the sound sound.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound sound
	 */
	public String getSoundSound(File file, String reward) {
		return getData(file, reward).getString("Sound.Sound");
	}

	/**
	 * Gets the sound volume.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound volume
	 */
	public float getSoundVolume(File file, String reward) {
		return (float) getData(file, reward).getDouble("Sound.Volume");
	}

	/**
	 * Gets the timed enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed enabled
	 */
	public boolean getTimedEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Timed.Enabled");
	}

	/**
	 * Gets the timed hour.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed hour
	 */
	public int getTimedHour(File file, String reward) {
		return getData(file, reward).getInt("Timed.Hour");
	}

	/**
	 * Gets the timed minute.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed minute
	 */
	public int getTimedMinute(File file, String reward) {
		return getData(file, reward).getInt("Timed.Minute");
	}

	/**
	 * Gets the title enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the title enabled
	 */
	public boolean getTitleEnabled(File file, String reward) {
		return getData(file, reward).getBoolean("Title.Enabled");
	}

	/**
	 * Gets the title fade in.
	 *
	 * @param reward
	 *            the reward
	 * @return the title fade in
	 */
	public int getTitleFadeIn(File file, String reward) {
		return getData(file, reward).getInt("Title.FadeIn");
	}

	/**
	 * Gets the title fade out.
	 *
	 * @param reward
	 *            the reward
	 * @return the title fade out
	 */
	public int getTitleFadeOut(File file, String reward) {
		return getData(file, reward).getInt("Title.FadeOut");
	}

	/**
	 * Gets the title show time.
	 *
	 * @param reward
	 *            the reward
	 * @return the title show time
	 */
	public int getTitleShowTime(File file, String reward) {
		return getData(file, reward).getInt("Title.ShowTime");
	}

	/**
	 * Gets the title sub title.
	 *
	 * @param reward
	 *            the reward
	 * @return the title sub title
	 */
	public String getTitleSubTitle(File file, String reward) {
		return getData(file, reward).getString("Title.SubTitle");
	}

	/**
	 * Gets the title title.
	 *
	 * @param reward
	 *            the reward
	 * @return the title title
	 */
	public String getTitleTitle(File file, String reward) {
		return getData(file, reward).getString("Title.Title");
	}

	/**
	 * Gets the worlds.
	 *
	 * @param reward
	 *            the reward
	 * @return the worlds
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getWorlds(File file, String reward) {

		return (ArrayList<String>) getData(file, reward).getList("Worlds",
				new ArrayList<String>());

	}

	@Deprecated
	public boolean isRewardValid(String reward) {
		return RewardHandler.getInstance().rewardExist(reward);
	}

	/**
	 * Checks if is reward valid.
	 *
	 * @param reward
	 *            the reward
	 * @return true, if is reward valid
	 */
	public boolean isRewardValid(File file, String reward) {
		File dFile = new File(plugin.getDataFolder() + File.separator
				+ "Rewards", reward + ".yml");
		return dFile.exists();
	}

	/**
	 * Sets the.
	 *
	 * @param reward
	 *            the reward
	 * @param path
	 *            the path
	 * @param value
	 *            the value
	 */
	public void set(File file, String reward, String path, Object value) {
		File dFile = getRewardFile(file, reward);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		data.set(path, value);
		FilesManager.getInstance().editFile(dFile, data);
	}

	public void set(String reward, String path, Object value) {
		set(new File(plugin.getDataFolder(), "Rewards"), reward, path, value);
	}

	/**
	 * Sets the chance.
	 *
	 * @param reward
	 *            the reward
	 * @param d
	 *            the d
	 */
	public void setChance(File file, String reward, double d) {
		set(reward, "Chance", d);
	}

	/**
	 * Sets the commands console.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setCommandsConsole(File file, String reward,
			ArrayList<String> value) {
		set(reward, "Commands.Console", value);
	}

	/**
	 * Sets the commands player.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setCommandsPlayer(File file, String reward,
			ArrayList<String> value) {
		set(reward, "Commands.Player", value);
	}

	/**
	 * Sets the EXP.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setEXP(File file, String reward, int value) {
		set(reward, "EXP", value);
	}

	/**
	 * Sets the give in each world.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setGiveInEachWorld(File file, String reward, boolean value) {
		set(reward, "GiveInEachWorld", value);
	}

	/**
	 * Sets the item amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemAmount(File file, String reward, String item, int value) {
		set(reward, "Items." + item + ".Amount", value);
	}

	/**
	 * Sets the item data.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemData(File file, String reward, String item, int value) {
		set(reward, "Items." + item + ".Data", value);
	}

	/**
	 * Sets the item durability.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemDurability(File file, String reward, String item,
			int value) {
		set(reward, "Items." + item + ".Durability", value);
	}

	/**
	 * Sets the item enchant.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param enchant
	 *            the enchant
	 * @param value
	 *            the value
	 */
	public void setItemEnchant(File file, String reward, String item,
			String enchant, int value) {
		set(reward, "Items." + item + ".Enchants." + enchant, value);
	}

	/**
	 * Sets the item lore.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemLore(File file, String reward, String item,
			ArrayList<String> value) {
		set(reward, "Items." + item + ".Lore", value);
	}

	/**
	 * Sets the item material.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMaterial(File file, String reward, String item,
			String value) {
		set(reward, "Items." + item + ".Material", value);
	}

	/**
	 * Sets the item max amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMaxAmount(File file, String reward, String item,
			int value) {
		set(reward, "Items." + item + ".MaxAmount", value);
	}

	/**
	 * Sets the item min amount.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemMinAmount(File file, String reward, String item,
			int value) {
		set(reward, "Items." + item + ".MinAmount", value);
	}

	/**
	 * Sets the item name.
	 *
	 * @param reward
	 *            the reward
	 * @param item
	 *            the item
	 * @param value
	 *            the value
	 */
	public void setItemName(File file, String reward, String item, String value) {
		set(reward, "Items." + item + ".Name", value);
	}

	/**
	 * Sets the max exp.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMaxExp(File file, String reward, int value) {
		set(reward, "MaxEXP", value);
	}

	/**
	 * Sets the max money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMaxMoney(File file, String reward, int value) {
		set(reward, "MaxMoney", value);
	}

	/**
	 * Sets the messages broadcast.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMessagesBroadcast(File file, String reward, String value) {
		set(reward, "Messages.Broadcast", value);
	}

	/**
	 * Sets the messages reward.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMessagesReward(File file, String reward, String value) {
		set(reward, "Messages.Reward", value);
	}

	/**
	 * Sets the min exp.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMinExp(File file, String reward, int value) {
		set(reward, "MinEXP", value);
	}

	/**
	 * Sets the min money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMinMoney(File file, String reward, int value) {
		set(reward, "MinMoney", value);
	}

	/**
	 * Sets the money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMoney(File file, String reward, int value) {
		set(reward, "Money", value);
	}

	public void setPermission(File file, String reward, String perm) {
		set(reward, "Permission", perm);
	}

	/**
	 * Sets the potions amplifier.
	 *
	 * @param reward
	 *            the reward
	 * @param potion
	 *            the potion
	 * @param value
	 *            the value
	 */
	public void setPotionsAmplifier(File file, String reward, String potion,
			int value) {
		set(reward, "Potions." + potion + ".Amplifier", value);
	}

	/**
	 * Sets the potions duration.
	 *
	 * @param reward
	 *            the reward
	 * @param potion
	 *            the potion
	 * @param value
	 *            the value
	 */
	public void setPotionsDuration(File file, String reward, String potion,
			int value) {
		set(reward, "Potions." + potion + ".Duration", value);
	}

	/**
	 * Sets the require permission.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setRequirePermission(File file, String reward, boolean value) {
		set(reward, "RequirePermission", value);
	}

	public void setRewardType(File file, String reward, String value) {
		set(reward, "RewardType", value);
	}

	/**
	 * Setup example.
	 */
	public void setupExample() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}

		copyFile("ExampleBasic.yml");
		copyFile("ExampleAdvanced.yml");
	}

	/**
	 * Sets the worlds.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setWorlds(File file, String reward, ArrayList<String> value) {
		set(reward, "Worlds", value);
	}

}
