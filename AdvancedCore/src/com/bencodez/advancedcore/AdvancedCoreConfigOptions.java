package com.bencodez.advancedcore;

import java.util.ArrayList;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.api.user.UserStorage;

import lombok.Getter;
import lombok.Setter;

public class AdvancedCoreConfigOptions {

	@Getter
	@Setter
	private boolean autoDownload = false;

	@Getter
	@Setter
	private double clickSoundPitch = 1;
	@Getter
	@Setter
	private Sound clickSoundSound = Sound.UI_BUTTON_CLICK;
	@Getter
	@Setter
	private double clickSoundVolume = 1;
	@Getter
	@Setter
	private ConfigurationSection configData;
	@Getter
	@Setter
	private boolean createBackups = true;

	@Getter
	@Setter
	private DebugLevel debug = DebugLevel.NONE;

	@Getter
	@Setter
	private boolean debugIngame = false;

	@Getter
	@Setter
	private String defaultRequestMethod = "ANVIL";
	@Getter
	@Setter
	private boolean disableCheckOnWorldChange = false;

	@Getter
	@Setter
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();

	@Getter
	@Setter
	private boolean dropOnFullInv = true;

	@Getter
	@Setter
	private boolean enableJenkins;

	@Getter
	@Setter
	@Deprecated
	private boolean extraDebug = false;

	@Getter
	@Setter
	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	@Getter
	@Setter
	private String formatInvFull;

	@Getter
	@Setter
	private String formatNoPerms = "&cYou do not have enough permission!";

	@Getter
	@Setter
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";

	@Getter
	private String formatRewardTimeFormat;

	@Getter
	@Setter
	private String geyserPrefix = "*";

	@Getter
	@Setter
	private boolean geyserPrefixSupport = false;

	@Getter
	@Setter
	private boolean onlineMode = true;

	@Getter
	@Setter
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	@Getter
	@Setter
	private boolean loadDefaultRewards = true;

	@Getter
	@Setter
	private boolean loadSkulls = true;

	@Getter
	@Setter
	private boolean logDebugToFile = false;

	@Getter
	@Setter
	private boolean multiplePermissionChecks = false;

	@Getter
	@Setter
	private int newLoreLength = 30;

	@Getter
	@Setter
	private ConfigurationSection nextItem;

	@Getter
	@Setter
	private String permPrefix;

	@Getter
	@Setter
	private boolean perServerRewards = false;

	@Getter
	@Setter
	private ConfigurationSection prevItem;

	@Getter
	@Setter
	private boolean processRewards = true;

	@Getter
	@Setter
	private int purgeMinimumDays = 90;

	@Getter
	@Setter
	private boolean purgeOldData = false;

	@Getter
	@Setter
	private int resourceId = 0;

	@Getter
	@Setter
	private boolean sendScoreboards = true;

	@Setter
	@Getter
	private String server = "";

	@Getter
	@Setter
	private String spamClickMessage = "";

	@Setter
	@Getter
	private int spamClickTime = 250;

	@Getter
	@Setter
	private UserStorage storageType = UserStorage.SQLITE;

	@Getter
	@Setter
	private int timeHourOffSet = 0;

	@Getter
	@Setter
	private String timeZone = "";

	@Getter
	@Setter
	private boolean treatVanishAsOffline = true;

	@Getter
	@Setter
	private boolean useVaultPermissions = false;

	@Getter
	@Setter
	private boolean waitUntilLoggedIn;

	@Getter
	@Setter
	private int delayLoginEvent = 0;

	@Getter
	@Setter
	private ArrayList<String> broadcastBlacklist = new ArrayList<String>();

	@Getter
	@Setter
	private boolean closeGUIOnShiftClick = false;;

	public AdvancedCoreConfigOptions() {
	}

	@SuppressWarnings("unchecked")
	public void load(AdvancedCorePlugin plugin) {
		if (getConfigData() != null) {
			debug = DebugLevel.getDebug(configData.getString("DebugLevel", "NONE"));
			if (debug.equals(DebugLevel.NONE)) {
				if (configData.getBoolean("Debug", false)) {
					if (configData.getBoolean("ExtraDebug", false)) {
						debug = DebugLevel.EXTRA;
					} else {
						debug = DebugLevel.INFO;
					}
				}
			}
			debugIngame = configData.getBoolean("DebugInGame", false);
			defaultRequestMethod = configData.getString("RequestAPI.DefaultMethod", "Anvil");
			disabledRequestMethods = (ArrayList<String>) configData.getList("RequestAPI.DisabledMethods",
					new ArrayList<String>());

			formatNoPerms = configData.getString("Format.NoPerms", "&cYou do not have enough permission!");
			formatNotNumber = configData.getString("Format.NotNumber", "&cError on &6%arg%&c, number expected!");
			formatInvFull = configData.getString("Format.InvFull", "&cInventory full");
			formatChoiceRewardsPreferenceSet = configData.getString("Format.ChoiceRewards.PreferenceSet",
					"&aPreference set to %choice%");

			helpLine = configData.getString("Format.HelpLine", "&6%Command% - &6%HelpMessage%");
			logDebugToFile = configData.getBoolean("LogDebugToFile", false);
			sendScoreboards = configData.getBoolean("SendScoreboards", true);
			prevItem = configData.getConfigurationSection("Format.PrevItem");
			nextItem = configData.getConfigurationSection("Format.NextItem");
			formatRewardTimeFormat = configData.getString("Format.RewardTimeFormat", "EEE, d MMM yyyy HH:mm");
			purgeOldData = configData.getBoolean("PurgeOldData");
			purgeMinimumDays = configData.getInt("PurgeMin", 90);
			disableCheckOnWorldChange = configData.getBoolean("DisableCheckOnWorldChange");
			autoDownload = configData.getBoolean("AutoDownload", false);
			extraDebug = configData.getBoolean("ExtraDebug", false);
			storageType = UserStorage.value(configData.getString("DataStorage", "SQLITE"));

			timeHourOffSet = configData.getInt("TimeHourOffSet", 0);
			timeZone = configData.getString("TimeZone", "");

			createBackups = configData.getBoolean("CreateBackups", false);

			enableJenkins = configData.getBoolean("JenkinsDownloadEnabled", true);
			processRewards = configData.getBoolean("ProcessRewards", true);

			waitUntilLoggedIn = configData.getBoolean("WaitUntilLoggedIn", true);
			broadcastBlacklist = (ArrayList<String>) configData.getList("BroadcastBlacklist", new ArrayList<String>());

			loadSkulls = configData.getBoolean("LoadSkulls", true);

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

			useVaultPermissions = configData.getBoolean("UseVaultPermissions", false);
			server = configData.getString("Server", "");

			newLoreLength = configData.getInt("NewLoreLength", 30);

			spamClickTime = configData.getInt("SpamClickTime", 100);

			spamClickMessage = configData.getString("SpamClickMessage", "");
			dropOnFullInv = configData.getBoolean("DropOnFullInv", true);
			multiplePermissionChecks = configData.getBoolean("MultiplePermissionsCheck", false);
			treatVanishAsOffline = configData.getBoolean("TreatVanishAsOffline", false);

			geyserPrefixSupport = configData.getBoolean("GeyserPrefixSupport", false);
			geyserPrefix = configData.getString("GeyserPrefix", "*");
			delayLoginEvent = configData.getInt("DelayLoginEvent", 0);

			onlineMode = configData.getBoolean("OnlineMode", true);
			closeGUIOnShiftClick = configData.getBoolean("CloseGUIOnShiftClick", false);
		}
	}
}
