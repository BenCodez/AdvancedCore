package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

public class RewardFileData {
	Main plugin = Main.plugin;

	private Reward reward;
	private File rewardFolder;
	private File dFile;
	private FileConfiguration data;

	public RewardFileData(Reward reward, File rewardFolder) {
		this.reward = reward;
		if (!rewardFolder.isDirectory()) {
			rewardFolder = rewardFolder.getParentFile();
		}
		this.rewardFolder = rewardFolder;
		setup();
	}

	/**
	 * Gets the action bar delay.
	 *
	 * @param reward
	 *            the reward
	 * @return the action bar delay
	 */
	public int getActionBarDelay() {
		return getData().getInt("ActionBar.Delay");
	}

	/**
	 * Gets the action bar message.
	 *
	 * @param reward
	 *            the reward
	 * @return the action bar message
	 */
	public String getActionBarMessage() {
		return getData().getString("ActionBar.Message");
	}

	/**
	 * Gets the boss bar color.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar color
	 */
	public String getBossBarColor() {
		return getData().getString("BossBar.Color");
	}

	/**
	 * Gets the boss bar delay.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar delay
	 */
	public int getBossBarDelay() {
		return getData().getInt("BossBar.Delay");
	}

	/**
	 * Gets the boss bar enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar enabled
	 */
	public boolean getBossBarEnabled() {
		return getData().getBoolean("BossBar.Enabled");
	}

	/**
	 * Gets the boss bar message.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar message
	 */
	public String getBossBarMessage() {
		return getData().getString("BossBar.Message");
	}

	/**
	 * Gets the boss bar progress.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar progress
	 */
	public double getBossBarProgress() {
		return getData().getDouble("BossBar.Progress");
	}

	/**
	 * Gets the boss bar style.
	 *
	 * @param reward
	 *            the reward
	 * @return the boss bar style
	 */
	public String getBossBarStyle() {
		return getData().getString("BossBar.Style");
	}

	/**
	 * Gets the chance.
	 *
	 * @param reward
	 *            the reward
	 * @return the chance
	 */
	public double getChance() {
		return getData().getDouble("Chance");
	}

	/**
	 * Gets the choice rewards enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the choice rewards enabled
	 */
	public boolean getChoiceRewardsEnabled() {
		return getData().getBoolean("ChoiceRewards.Enabled");
	}

	/**
	 * Gets the choice rewards rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the choice rewards rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getChoiceRewardsRewards() {
		return (ArrayList<String>) getData().getList("ChoiceRewards.Rewards",
				new ArrayList<String>());
	}

	/**
	 * Gets the commands console.
	 *
	 * @param reward
	 *            the reward
	 * @return the commands console
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsConsole() {

		return (ArrayList<String>) getData().getList("Commands.Console",
				new ArrayList<String>());

	}

	/**
	 * Gets the commands player.
	 *
	 * @param reward
	 *            the reward
	 * @return the commands player
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getCommandsPlayer() {

		return (ArrayList<String>) getData().getList("Commands.Player",
				new ArrayList<String>());

	}

	public FileConfiguration getData() {
		return data;
	}

	/**
	 * Gets the delayed enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed enabled
	 */
	public boolean getDelayedEnabled() {
		return getData().getBoolean("Delayed.Enabled");
	}

	/**
	 * Gets the delayed hours.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed hours
	 */
	public int getDelayedHours() {
		return getData().getInt("Delayed.Hours");
	}

	/**
	 * Gets the delayed minutes.
	 *
	 * @param reward
	 *            the reward
	 * @return the delayed minutes
	 */
	public int getDelayedMinutes() {
		return getData().getInt("Delayed.Minutes");
	}

	/**
	 * Gets the effect data.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect data
	 */
	public int getEffectData() {
		return getData().getInt("Effect.Data");
	}

	/**
	 * Gets the effect effect.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect effect
	 */
	public String getEffectEffect() {
		return getData().getString("Effect.Effect", "");

	}

	/**
	 * Gets the effect enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect enabled
	 */
	public boolean getEffectEnabled() {
		return getData().getBoolean("Effect.Enabled");
	}

	/**
	 * Gets the effect particles.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect particles
	 */
	public int getEffectParticles() {
		return getData().getInt("Effect.Particles");
	}

	/**
	 * Gets the effect radius.
	 *
	 * @param reward
	 *            the reward
	 * @return the effect radius
	 */
	public int getEffectRadius() {
		return getData().getInt("Effect.Radius");
	}

	/**
	 * Gets the exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the exp
	 */
	public int getEXP() {
		return getData().getInt("EXP");
	}

	/**
	 * Gets the firework colors.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework colors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColors() {
		return (ArrayList<String>) getData().getList("Firework.Colors",
				new ArrayList<String>());
	}

	/**
	 * Gets the firework colors fade out.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework colors fade out
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkColorsFadeOut() {
		return (ArrayList<String>) getData().getList("Firework.FadeOutColor",
				new ArrayList<String>());
	}

	/**
	 * Gets the firework enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework enabled
	 */
	public boolean getFireworkEnabled() {
		return getData().getBoolean("Firework.Enabled");
	}

	/**
	 * Gets the firework flicker.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework flicker
	 */
	public boolean getFireworkFlicker() {
		return getData().getBoolean("Firework.Flicker");
	}

	/**
	 * Gets the firework power.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework power
	 */
	public int getFireworkPower() {
		return getData().getInt("Firework.Power");
	}

	/**
	 * Gets the firework trail.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework trail
	 */
	public boolean getFireworkTrail() {
		return getData().getBoolean("Firework.Trail");
	}

	/**
	 * Gets the firework types.
	 *
	 * @param reward
	 *            the reward
	 * @return the firework types
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getFireworkTypes() {
		return (ArrayList<String>) getData().getList("Firework.Types",
				new ArrayList<String>());
	}

	/**
	 * Gets the give in each world.
	 *
	 * @param reward
	 *            the reward
	 * @return the give in each world
	 */
	public boolean getGiveInEachWorld() {
		return getData().getBoolean("GiveInEachWorld");
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
	public int getItemAmount(String item) {
		return getData().getInt("Items." + item + ".Amount");
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
	public int getItemData(String item) {
		return getData().getInt("Items." + item + ".Data");
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
	public int getItemDurability(String item) {
		return getData().getInt("Items." + item + ".Durability");
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
	public Set<String> getItemEnchants(String item) {
		try {
			return getData().getConfigurationSection(
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
	public int getItemEnchantsLevel(String item, String enchant) {
		return getData().getInt("Items." + item + ".Enchants." + enchant);
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
	public ArrayList<String> getItemLore(String item) {
		return (ArrayList<String>) getData().getList("Items." + item + ".Lore");
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
	public String getItemMaterial(String item) {
		return getData().getString("Items." + item + ".Material");
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
	public int getItemMaxAmount(String item) {
		return getData().getInt("Items." + item + ".MaxAmount");
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
	public int getItemMinAmount(String item) {
		return getData().getInt("Items." + item + ".MinAmount");
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
	public String getItemName(String item) {
		return getData().getString("Items." + item + ".Name");
	}

	/**
	 * Gets the items.
	 *
	 * @param reward
	 *            the reward
	 * @return the items
	 */
	public Set<String> getItems() {
		try {
			return getData().getConfigurationSection("Items").getKeys(false);
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
	public String getItemSkull(String item) {
		return getData().getString("Items." + item + ".Skull");
	}

	/**
	 * Gets the javascript enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript enabled
	 */
	public boolean getJavascriptEnabled() {
		return getData().getBoolean("Javascript.Enabled");
	}

	/**
	 * Gets the javascript expression.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript expression
	 */
	public String getJavascriptExpression() {
		return getData().getString("Javascript.Expression", "");
	}

	/**
	 * Gets the javascript false rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript false rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascriptFalseRewards() {
		return (ArrayList<String>) getData().getList("Javascript.FalseRewards",
				new ArrayList<String>());
	}

	/**
	 * Gets the javascript true rewards.
	 *
	 * @param reward
	 *            the reward
	 * @return the javascript true rewards
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getJavascriptTrueRewards() {
		return (ArrayList<String>) getData().getList("Javascript.TrueRewards",
				new ArrayList<String>());
	}

	/**
	 * Gets the max exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the max exp
	 */
	public int getMaxExp() {
		return getData().getInt("MaxEXP");
	}

	/**
	 * Gets the max money.
	 *
	 * @param reward
	 *            the reward
	 * @return the max money
	 */
	public int getMaxMoney() {
		return getData().getInt("MaxMoney");
	}

	/**
	 * Gets the messages broadcast.
	 *
	 * @param reward
	 *            the reward
	 * @return the messages broadcast
	 */
	public String getMessagesBroadcast() {
		return getData().getString("Messages.Broadcast", "");
	}

	/**
	 * Gets the messages reward.
	 *
	 * @param reward
	 *            the reward
	 * @return the messages reward
	 */
	public String getMessagesReward() {
		String msg = getData().getString("Messages.Reward", "");
		return msg;

	}

	/**
	 * Gets the min exp.
	 *
	 * @param reward
	 *            the reward
	 * @return the min exp
	 */
	public int getMinExp() {
		return getData().getInt("MinEXP");
	}

	/**
	 * Gets the min money.
	 *
	 * @param reward
	 *            the reward
	 * @return the min money
	 */
	public int getMinMoney() {
		return getData().getInt("MinMoney");
	}

	/**
	 * Gets the money.
	 *
	 * @param reward
	 *            the reward
	 * @return the money
	 */
	public int getMoney() {
		return getData().getInt("Money");
	}

	/**
	 * Gets the permission.
	 *
	 * @param reward
	 *            the reward
	 * @return the permission
	 */
	public String getPermission() {
		return getData().getString("Permission",
				"AdvancedCore.Reward." + reward);
	}

	/**
	 * Gets the potions.
	 *
	 * @param reward
	 *            the reward
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
	 * @param reward
	 *            the reward
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
	 * @param reward
	 *            the reward
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
	 * @param reward
	 *            the reward
	 * @return the random chance
	 */
	public double getRandomChance() {
		return getData().getDouble("Random.Chance");
	}

	/**
	 * Gets the random fall back.
	 *
	 * @param reward
	 *            the reward
	 * @return the random fall back
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getRandomFallBack() {
		try {
			return (ArrayList<String>) getData().getList("Random.FallBack");
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
	public ArrayList<String> getRandomRewards() {

		return (ArrayList<String>) getData().getList("Random.Rewards",
				new ArrayList<String>());

	}

	/**
	 * Gets the require permission.
	 *
	 * @param reward
	 *            the reward
	 * @return the require permission
	 */
	public boolean getRequirePermission() {
		return getData().getBoolean("RequirePermission");
	}

	public void setup() {
		if (dFile == null) {
			dFile = new File(rewardFolder, reward.getRewardName() + ".yml");
		}
		data = YamlConfiguration.loadConfiguration(dFile);
		if (!dFile.exists()) {
			try {
				data.save(dFile);
			} catch (IOException e) {
				plugin.getLogger().severe(
						ChatColor.RED + "Could not create "
								+ dFile.getAbsolutePath());

			}
		}

	}

	public void reload() {
		data = YamlConfiguration.loadConfiguration(dFile);
	}

	/**
	 * Gets the reward type.
	 *
	 * @param reward
	 *            the reward
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
	 * @param reward
	 *            the reward
	 * @return the sound enabled
	 */
	public boolean getSoundEnabled() {
		return getData().getBoolean("Sound.Enabled");
	}

	/**
	 * Gets the sound pitch.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound pitch
	 */
	public float getSoundPitch() {
		return (float) getData().getDouble("Sound.Pitch");
	}

	/**
	 * Gets the sound sound.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound sound
	 */
	public String getSoundSound() {
		return getData().getString("Sound.Sound");
	}

	/**
	 * Gets the sound volume.
	 *
	 * @param reward
	 *            the reward
	 * @return the sound volume
	 */
	public float getSoundVolume() {
		return (float) getData().getDouble("Sound.Volume");
	}

	/**
	 * Gets the timed enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed enabled
	 */
	public boolean getTimedEnabled() {
		return getData().getBoolean("Timed.Enabled");
	}

	/**
	 * Gets the timed hour.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed hour
	 */
	public int getTimedHour() {
		return getData().getInt("Timed.Hour");
	}

	/**
	 * Gets the timed minute.
	 *
	 * @param reward
	 *            the reward
	 * @return the timed minute
	 */
	public int getTimedMinute() {
		return getData().getInt("Timed.Minute");
	}

	/**
	 * Gets the title enabled.
	 *
	 * @param reward
	 *            the reward
	 * @return the title enabled
	 */
	public boolean getTitleEnabled() {
		return getData().getBoolean("Title.Enabled");
	}

	/**
	 * Gets the title fade in.
	 *
	 * @param reward
	 *            the reward
	 * @return the title fade in
	 */
	public int getTitleFadeIn() {
		return getData().getInt("Title.FadeIn");
	}

	/**
	 * Gets the title fade out.
	 *
	 * @param reward
	 *            the reward
	 * @return the title fade out
	 */
	public int getTitleFadeOut() {
		return getData().getInt("Title.FadeOut");
	}

	/**
	 * Gets the title show time.
	 *
	 * @param reward
	 *            the reward
	 * @return the title show time
	 */
	public int getTitleShowTime() {
		return getData().getInt("Title.ShowTime");
	}

	/**
	 * Gets the title sub title.
	 *
	 * @param reward
	 *            the reward
	 * @return the title sub title
	 */
	public String getTitleSubTitle() {
		return getData().getString("Title.SubTitle");
	}

	/**
	 * Gets the title title.
	 *
	 * @param reward
	 *            the reward
	 * @return the title title
	 */
	public String getTitleTitle() {
		return getData().getString("Title.Title");
	}

	/**
	 * Gets the worlds.
	 *
	 * @param reward
	 *            the reward
	 * @return the worlds
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getWorlds() {

		return (ArrayList<String>) getData().getList("Worlds",
				new ArrayList<String>());

	}

	public void set(String path, Object value) {
		data.set(path, value);
		FilesManager.getInstance().editFile(dFile, data);
		reload();
	}

	/**
	 * Sets the chance.
	 *
	 * @param reward
	 *            the reward
	 * @param d
	 *            the d
	 */
	public void setChance(double d) {
		set("Chance", d);
	}

	/**
	 * Sets the commands console.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setCommandsConsole(ArrayList<String> value) {
		set("Commands.Console", value);
	}

	/**
	 * Sets the commands player.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setCommandsPlayer(ArrayList<String> value) {
		set("Commands.Player", value);
	}

	/**
	 * Sets the EXP.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setEXP(int value) {
		set("EXP", value);
	}

	/**
	 * Sets the give in each world.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setGiveInEachWorld(boolean value) {
		set("GiveInEachWorld", value);
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
	public void setItemAmount(String item, int value) {
		set("Items." + item + ".Amount", value);
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
	public void setItemData(String item, int value) {
		set("Items." + item + ".Data", value);
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
	public void setItemDurability(String item, int value) {
		set("Items." + item + ".Durability", value);
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
	public void setItemEnchant(String item, String enchant, int value) {
		set("Items." + item + ".Enchants." + enchant, value);
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
	public void setItemLore(String item, ArrayList<String> value) {
		set("Items." + item + ".Lore", value);
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
	public void setItemMaterial(String item, String value) {
		set("Items." + item + ".Material", value);
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
	public void setItemMaxAmount(String item, int value) {
		set("Items." + item + ".MaxAmount", value);
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
	public void setItemMinAmount(String item, int value) {
		set("Items." + item + ".MinAmount", value);
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
	public void setItemName(String item, String value) {
		set("Items." + item + ".Name", value);
	}

	/**
	 * Sets the max exp.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMaxExp(int value) {
		set("MaxEXP", value);
	}

	/**
	 * Sets the max money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMaxMoney(int value) {
		set("MaxMoney", value);
	}

	/**
	 * Sets the messages broadcast.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMessagesBroadcast(String value) {
		set("Messages.Broadcast", value);
	}

	/**
	 * Sets the messages reward.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMessagesReward(String value) {
		set("Messages.Reward", value);
	}

	/**
	 * Sets the min exp.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMinExp(int value) {
		set("MinEXP", value);
	}

	/**
	 * Sets the min money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMinMoney(int value) {
		set("MinMoney", value);
	}

	/**
	 * Sets the money.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setMoney(int value) {
		set("Money", value);
	}

	public void setPermission(String perm) {
		set("Permission", perm);
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
	public void setPotionsAmplifier(String potion, int value) {
		set("Potions." + potion + ".Amplifier", value);
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
	public void setPotionsDuration(String potion, int value) {
		set("Potions." + potion + ".Duration", value);
	}

	/**
	 * Sets the require permission.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setRequirePermission(boolean value) {
		set("RequirePermission", value);
	}

	public void setRewardType(String value) {
		set("RewardType", value);
	}

	/**
	 * Sets the worlds.
	 *
	 * @param reward
	 *            the reward
	 * @param value
	 *            the value
	 */
	public void setWorlds(ArrayList<String> value) {
		set("Worlds", value);
	}
}
