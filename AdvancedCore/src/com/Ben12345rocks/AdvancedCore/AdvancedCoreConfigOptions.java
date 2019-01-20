package com.Ben12345rocks.AdvancedCore;

import java.util.ArrayList;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;

import lombok.Getter;
import lombok.Setter;

public class AdvancedCoreConfigOptions {

	@Getter
	@Setter
	private boolean debug = false;

	@Getter
	@Setter
	private boolean debugIngame = false;
	@Getter
	@Setter
	private boolean logDebugToFile = true;
	@Getter
	@Setter
	private String defaultRequestMethod = "ANVIL";
	@Getter
	@Setter
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();
	@Getter
	@Setter
	private String formatNoPerms = "&cYou do not have enough permission!";
	@Getter
	@Setter
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";
	@Getter
	@Setter
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	@Getter
	@Setter
	private String permPrefix;

	@Getter
	@Setter
	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	@Getter
	@Setter
	private boolean extraDebug = false;

	@Getter
	@Setter
	private boolean disableCheckOnWorldChange = false;
	@Getter
	@Setter
	private boolean sendScoreboards = true;

	@Getter
	@Setter
	private boolean autoKillInvs = true;

	@Getter
	@Setter
	private String prevPageTxt = "&aPrevious Page";
	@Getter
	@Setter
	private String nextPageTxt = "&aNext Page";

	@Deprecated
	@Getter
	@Setter
	private boolean checkNameMojang = false;

	@Getter
	@Setter
	private boolean alternateUUIDLookUp;

	@Getter
	@Setter
	private boolean purgeOldData = false;

	@Getter
	@Setter
	private int purgeMinimumDays = 90;

	@Getter
	@Setter
	private String formatInvFull;

	@Getter
	@Setter
	private int timeHourOffSet = 0;

	@Getter
	@Setter
	private boolean createBackups = true;

	@Getter
	@Setter
	private boolean enableJenkins;

	@Getter
	@Setter
	private ConfigurationSection configData;

	@Getter
	@Setter
	private boolean autoDownload = false;

	@Getter
	@Setter
	private UserStorage storageType = UserStorage.SQLITE;
	@Getter
	@Setter
	private int resourceId = 0;

	@Getter
	@Setter
	private boolean processRewards = true;

	@Getter
	@Setter
	private boolean clearCacheOnJoin;

	@Getter
	@Setter
	private boolean loadDefaultRewards = true;

	@Getter
	@Setter
	private Sound clickSoundSound = Sound.UI_BUTTON_CLICK;

	@Getter
	@Setter
	private double clickSoundVolume = 1;

	@Getter
	@Setter
	private double clickSoundPitch = 1;

	@Getter
	@Setter
	private boolean preloadSkulls;

	public AdvancedCoreConfigOptions() {
	}

	@SuppressWarnings("unchecked")
	public void load() {
		if (getConfigData() != null) {
			debug = configData.getBoolean("Debug", false);
			debugIngame = configData.getBoolean("DebugInGame", false);
			defaultRequestMethod = configData.getString("RequestAPI.DefaultMethod", "Anvil");
			disabledRequestMethods = (ArrayList<String>) configData.getList("RequestAPI.DisabledMethods",
					new ArrayList<String>());

			formatNoPerms = configData.getString("Format.NoPerms", "&cYou do not have enough permission!");
			formatNotNumber = configData.getString("Format.NotNumber", "&cError on &6%arg%&c, number expected!");
			formatInvFull = configData.getString("Format.InvFull", "&cInventory full, dropping items on ground");
			formatChoiceRewardsPreferenceSet = configData.getString("Format.ChoiceRewards.PreferenceSet",
					"&aPreference set to %choice%");

			helpLine = configData.getString("Format.HelpLine", "&3&l%Command% - &3%HelpMessage%");
			logDebugToFile = configData.getBoolean("LogDebugToFile", false);
			sendScoreboards = configData.getBoolean("SendScoreboards", true);
			alternateUUIDLookUp = configData.getBoolean("AlternateUUIDLookup", false);
			autoKillInvs = configData.getBoolean("AutoKillInvs", true);
			prevPageTxt = configData.getString("Format.PrevPage", "&aPrevious Page");
			nextPageTxt = configData.getString("Format.NextPage", "&aNext Page");
			purgeOldData = configData.getBoolean("PurgeOldData");
			purgeMinimumDays = configData.getInt("PurgeMin", 90);
			checkNameMojang = configData.getBoolean("CheckNameMojang", false);
			if (checkNameMojang) {
				AdvancedCoreHook.getInstance().getPlugin().getLogger()
						.info("Using mojang name lookups allowed, disable if you run into issues");
			}
			disableCheckOnWorldChange = configData.getBoolean("DisableCheckOnWorldChange");
			autoDownload = configData.getBoolean("AutoDownload", false);
			extraDebug = configData.getBoolean("ExtraDebug", false);
			storageType = UserStorage.value(configData.getString("DataStorage", "SQLITE"));

			timeHourOffSet = configData.getInt("TimeHourOffSet", 0);

			createBackups = configData.getBoolean("CreateBackups", false);

			enableJenkins = configData.getBoolean("JenkinsDownloadEnabled");
			processRewards = configData.getBoolean("ProcessRewards", true);
			clearCacheOnJoin = configData.getBoolean("ClearCacheOnJoin", false);
			preloadSkulls = configData.getBoolean("PreloadSkulls", true);

			ConfigurationSection soundData = configData.getConfigurationSection("ClickSound");
			if (soundData != null) {
				try {
					String str = configData.getString("Sound", "UI_BUTTON_CLICK");
					if (str.equalsIgnoreCase("none")) {
						clickSoundSound = null;
					} else {
						clickSoundVolume = configData.getDouble("Volume", 1);
						clickSoundPitch = configData.getDouble("Pitch", 1);
					}
					clickSoundSound = Sound.valueOf(str);
				} catch (Exception e) {
					e.printStackTrace();
					clickSoundSound = Sound.UI_BUTTON_CLICK;
				}
			}
		}
	}
}
