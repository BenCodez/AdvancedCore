package com.bencodez.advancedcore;

import java.util.ArrayList;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.yml.YMLConfig;
import com.bencodez.advancedcore.api.yml.annotation.AnnotationHandler;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataBoolean;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataConfigurationSection;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataDouble;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataInt;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataListString;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataString;

import lombok.Getter;
import lombok.Setter;

public class AdvancedCoreConfigOptions {

	@Getter
	@Setter
	private YMLConfig ymlConfig;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "AutoDownload")
	private boolean autoDownload = false;

	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Pitch")
	private double clickSoundPitch = 1;
	@Getter
	@Setter
	@ConfigDataString(path = "ClickSound.Sound")
	private String clickSoundSoundStr = Sound.UI_BUTTON_CLICK.toString();

	public Sound getClickSoundSound() {
		try {
			if (getClickSoundSoundStr().equalsIgnoreCase("none")) {
				return null;
			} else {
				return Sound.valueOf(getClickSoundSoundStr());
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Sound.UI_BUTTON_CLICK;
		}
	}

	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Volume")
	private double clickSoundVolume = 1;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "CreateBackups")
	private boolean createBackups = false;
	
	@Getter
	@Setter
	@ConfigDataBoolean(path = "AlternateOnlineLookup")
	private boolean alternateOnlineLookup = false;

	@ConfigDataString(path = "DebugLevel", options = { "NONE", "INFO", "EXTRA", "DEV" })
	private String debugLevelStr = "NONE";

	@Getter
	@Setter
	private DebugLevel debug = DebugLevel.NONE;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "DebugInGame")
	private boolean debugIngame = false;

	@Getter
	@Setter
	@ConfigDataString(path = "RequestAPI.DefaultMethod")
	private String defaultRequestMethod = "ANVIL";
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableCheckOnWorldChange")
	private boolean disableCheckOnWorldChange = false;

	@Getter
	@Setter
	@ConfigDataListString(path = "RequestAPI.DisabledMethods")
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();

	@Getter
	@Setter
	@ConfigDataBoolean(path = "DropOnFullInv")
	private boolean dropOnFullInv = true;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "JenkinsDownloadEnabled")
	private boolean enableJenkins = false;

	@Getter
	@Setter
	@ConfigDataString(path = "Format.ChoiceRewards.PreferenceSet")
	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	@Getter
	@Setter
	@ConfigDataString(path = "Format.InvFull")
	private String formatInvFull = "&cInventory full";

	@Getter
	@Setter
	@ConfigDataString(path = "Format.NoPerms")
	private String formatNoPerms = "&cYou do not have enough permission!";

	@Getter
	@Setter
	@ConfigDataString(path = "Format.NotNumber")
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";

	@Getter
	@Setter
	@ConfigDataString(path = "Format.RewardTimeFormat")
	private String formatRewardTimeFormat = "EEE, d MMM yyyy HH:mm";

	@Getter
	@Setter
	@ConfigDataString(path = "BedrockPlayerPrefix")
	private String bedrockPlayerPrefix = ".";

	@Getter
	@Setter
	@ConfigDataBoolean(path = "OnlineMode")
	private boolean onlineMode = true;

	@Getter
	@Setter
	@ConfigDataString(path = "Format.HelpLine")
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean loadDefaultRewards = true;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "LoadSkulls")
	private boolean loadSkulls = true;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "LogDebugToFile")
	private boolean logDebugToFile = false;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean multiplePermissionChecks = false;

	@Getter
	@Setter
	@ConfigDataInt(path = "")
	private int newLoreLength = 30;

	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.NextItem")
	private ConfigurationSection nextItem;

	@Getter
	@Setter
	private String permPrefix;

	@Getter
	@Setter
	private boolean perServerRewards = false;

	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.PrevItem")
	private ConfigurationSection prevItem;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "ProcessRewards")
	private boolean processRewards = true;

	@Getter
	@Setter
	@ConfigDataInt(path = "PurgeMin")
	private int purgeMinimumDays = 90;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "PurgeOldData")
	private boolean purgeOldData = false;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableJavascript")
	private boolean disableJavascript = false;

	@Getter
	@Setter
	private int resourceId = 0;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "SendScoreboards")
	private boolean sendScoreboards = true;

	@Setter
	@Getter
	@ConfigDataString(path = "Server")
	private String server = "";

	@Getter
	@Setter
	@ConfigDataString(path = "SpamClickMessage")
	private String spamClickMessage = "";

	@Setter
	@Getter
	@ConfigDataInt(path = "SpamClickTime")
	private int spamClickTime = 100;

	@ConfigDataString(path = "DataStorage")
	private String userStorageString = "SQLITE";

	@Getter
	@Setter
	private UserStorage storageType = UserStorage.SQLITE;

	@Getter
	@Setter
	@ConfigDataInt(path = "TimeHourOffSet")
	private int timeHourOffSet = 0;

	@Getter
	@Setter
	@ConfigDataString(path = "TimeZone")
	private String timeZone = "";

	@Getter
	@Setter
	@ConfigDataBoolean(path = "TreatVanishAsOffline")
	private boolean treatVanishAsOffline = true;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "UseVaultPermissions")
	private boolean useVaultPermissions = false;

	@Getter
	@Setter
	@ConfigDataBoolean(path = "WaitUntilLoggedIn")
	private boolean waitUntilLoggedIn;

	@Getter
	@Setter
	@ConfigDataInt(path = "DelayLoginEvent")
	private int delayLoginEvent = 0;

	@Getter
	@Setter
	@ConfigDataListString(path = "BroadcastBlacklist")
	private ArrayList<String> broadcastBlacklist = new ArrayList<String>();

	@Getter
	@Setter
	@ConfigDataBoolean(path = "CloseGUIOnShiftClick")
	private boolean closeGUIOnShiftClick = false;

	@Getter
	@Setter
	@ConfigDataListString(path = "DefaultRewardWorlds")
	private ArrayList<String> defaultRewardWorlds = new ArrayList<String>();

	@Getter
	@Setter
	@ConfigDataListString(path = "DefaultRewardBlackListedWorlds")
	private ArrayList<String> defaultRewardBlackListedWorlds = new ArrayList<String>();

	public AdvancedCoreConfigOptions() {
	}

	public void load(AdvancedCorePlugin plugin) {
		if (getYmlConfig() != null) {
			new AnnotationHandler().load(getYmlConfig().getData(), this);
			debug = DebugLevel.getDebug(debugLevelStr);
			storageType = UserStorage.value(userStorageString.toUpperCase());
		}
	}
}
