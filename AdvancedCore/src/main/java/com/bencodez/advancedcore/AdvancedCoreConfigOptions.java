package com.bencodez.advancedcore;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.debug.DebugLevel;
import com.bencodez.simpleapi.file.YMLConfig;
import com.bencodez.simpleapi.file.annotation.AnnotationHandler;
import com.bencodez.simpleapi.file.annotation.ConfigDataBoolean;
import com.bencodez.simpleapi.file.annotation.ConfigDataConfigurationSection;
import com.bencodez.simpleapi.file.annotation.ConfigDataDouble;
import com.bencodez.simpleapi.file.annotation.ConfigDataInt;
import com.bencodez.simpleapi.file.annotation.ConfigDataListString;
import com.bencodez.simpleapi.file.annotation.ConfigDataString;
import com.bencodez.simpleapi.time.ParsedDuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration options for AdvancedCore plugin.
 * This class manages all configuration settings loaded from the config file.
 */
public class AdvancedCoreConfigOptions {

	/**
	 * The YML configuration file handler.
	 */
	@Getter
	@Setter
	private YMLConfig ymlConfig;

	/**
	 * Whether rewards are currently paused.
	 */
	@Getter
	@Setter
	private boolean pauseRewards = false;

	/**
	 * The pitch of the click sound (0.5 to 2.0).
	 */
	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Pitch")
	private double clickSoundPitch = 1;
	/**
	 * The sound string identifier for the click sound.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "ClickSound.Sound")
	private String clickSoundSoundStr = "ui.button.click";

	/**
	 * The volume of the click sound (0.0 to 1.0).
	 */
	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Volume")
	private double clickSoundVolume = 1;

	/**
	 * Whether to create backup files of configuration.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "CreateBackups")
	private boolean createBackups = false;

	@ConfigDataString(path = "DebugLevel", options = { "NONE", "INFO", "EXTRA", "DEV" })
	private String debugLevelStr = "NONE";

	/**
	 * The current debug level for logging.
	 */
	@Getter
	@Setter
	private DebugLevel debug = DebugLevel.NONE;

	/**
	 * The default request method for user input (ANVIL, CHAT, etc).
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "RequestAPI.DefaultMethod")
	private String defaultRequestMethod = "ANVIL";

	/**
	 * Whether to disable reward checks on world change events.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableCheckOnWorldChange")
	private boolean disableCheckOnWorldChange = false;
	/**
	 * List of disabled request methods.
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "RequestAPI.DisabledMethods")
	private ArrayList<String> disabledRequestMethods = new ArrayList<>();

	/**
	 * Whether to drop items on ground when inventory is full.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DropOnFullInv")
	private boolean dropOnFullInv = true;

	/**
	 * Whether Jenkins download functionality is enabled.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "JenkinsDownloadEnabled")
	private boolean enableJenkins = false;

	/**
	 * Format string for choice rewards preference set message.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.ChoiceRewards.PreferenceSet")
	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	/**
	 * Format string for inventory full message.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.InvFull")
	private String formatInvFull = "&cInventory full";

	/**
	 * Format string for no permissions message.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.NoPerms")
	private String formatNoPerms = "&cYou do not have enough permission!";

	/**
	 * Format string for not a number error message.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.NotNumber")
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";

	/**
	 * Time format string for displaying reward times.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.RewardTimeFormat")
	private String formatRewardTimeFormat = "EEE, d MMM yyyy HH:mm";

	/**
	 * Prefix for Bedrock Edition player names.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "BedrockPlayerPrefix")
	private String bedrockPlayerPrefix = ".";

	/**
	 * Whether the server is running in online mode.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "OnlineMode")
	private boolean onlineMode = true;

	/**
	 * Format string for help command lines.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.HelpLine")
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";

	/**
	 * Whether to load default rewards on startup.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean loadDefaultRewards = true;

	/**
	 * Whether to check multiple permission nodes for rewards.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean multiplePermissionChecks = false;

	/**
	 * Maximum length for item lore lines before wrapping.
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "")
	private int newLoreLength = 30;

	/**
	 * Configuration section for the next page button in GUIs.
	 */
	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.NextItem")
	private ConfigurationSection nextItem;

	/**
	 * Permission prefix for plugin permissions.
	 */
	@Getter
	@Setter
	private String permPrefix;

	/**
	 * Whether rewards are specific to each server in a network.
	 */
	@Getter
	@Setter
	private boolean perServerRewards = false;

	/**
	 * Configuration section for the previous page button in GUIs.
	 */
	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.PrevItem")
	private ConfigurationSection prevItem;

	/**
	 * Whether to process rewards when giving them to players.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "ProcessRewards")
	private boolean processRewards = true;

	/**
	 * Minimum number of days before player data can be purged.
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "PurgeMin")
	private int purgeMinimumDays = 90;

	/**
	 * Whether to automatically purge old player data.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "PurgeOldData")
	private boolean purgeOldData = false;

	/**
	 * Whether to purge old data immediately on server startup.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "PurgeDataOnStartup")
	private boolean purgeDataOnStartup = false;

	/**
	 * API URL for fetching player skull profiles.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SkullProfileAPIURL")
	private String SkullProfileAPIURL = "";

	/**
	 * Whether JavaScript execution is disabled.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableJavascript")
	private boolean disableJavascript = false;

	/**
	 * Whether the JavaScript command is enabled.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "EnableJavascriptCommand")
	private boolean enableJavascriptCommand = false;

	/**
	 * Spigot resource ID for update checking.
	 */
	@Getter
	@Setter
	private int resourceId = 0;

	/**
	 * Whether to send scoreboard packets to players.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "SendScoreboards")
	private boolean sendScoreboards = true;

	/**
	 * Server name identifier for multi-server setups.
	 */
	@Setter
	@Getter
	@ConfigDataString(path = "Server")
	private String server = "";

	/**
	 * Message to display when a player spam-clicks.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SpamClickMessage")
	private String spamClickMessage = "";

	/**
	 * Time duration string for spam click detection.
	 */
	@Setter
	@Getter
	@ConfigDataString(path = "SpamClickTime")
	private String spamClickTime = "100ms";

	@ConfigDataString(path = "DataStorage")
	private String userStorageString = "SQLITE";

	/**
	 * The storage type for user data (SQLITE, MYSQL, etc).
	 */
	@Getter
	@Setter
	private UserStorage storageType = UserStorage.SQLITE;

	/**
	 * Hour offset for time-based features.
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "TimeHourOffSet")
	private int timeHourOffSet = 0;

	/**
	 * Week offset for time-based features.
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "TimeWeekOffSet")
	private int timeWeekOffSet = 0;

	/**
	 * Whether to bypass fail-safe checks for time changes.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "TimeChangeFailSafeBypass")
	private boolean timeChangeFailSafeBypass = false;

	/**
	 * Time zone string for time-based features.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "TimeZone")
	private String timeZone = "";

	/**
	 * Whether to treat vanished players as offline.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "TreatVanishAsOffline")
	private boolean treatVanishAsOffline = true;

	/**
	 * Whether to use Vault for permission checks.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "UseVaultPermissions")
	private boolean useVaultPermissions = false;

	/**
	 * Whether to wait until players are logged in before processing rewards.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "WaitUntilLoggedIn")
	private boolean waitUntilLoggedIn;

	/**
	 * Delay duration string before processing login events.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "DelayLoginEvent")
	private String delayLoginEvent = "0s";

	/**
	 * Delay duration string before loading player skulls.
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SkullLoadDelay")
	private String SkullLoadDelay = "4s";

	/**
	 * List of worlds where broadcasts are disabled.
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "BroadcastBlacklist")
	private ArrayList<String> broadcastBlacklist = new ArrayList<>();

	/**
	 * Whether to close GUIs when shift-clicking items.
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "CloseGUIOnShiftClick")
	private boolean closeGUIOnShiftClick = false;

	/**
	 * List of worlds where default rewards are enabled.
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "DefaultRewardWorlds")
	private ArrayList<String> defaultRewardWorlds = new ArrayList<>();

	/**
	 * List of worlds where default rewards are disabled.
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "DefaultRewardBlackListedWorlds")
	private ArrayList<String> defaultRewardBlackListedWorlds = new ArrayList<>();

	/**
	 * Constructs a new AdvancedCoreConfigOptions with default values.
	 */
	public AdvancedCoreConfigOptions() {
	}

	/**
	 * Delay in milliseconds before processing login events.
	 */
	@Getter
	private long delayLoginEventMs = 0L;
	/**
	 * Time in milliseconds for spam click detection.
	 */
	@Getter
	private long spamClickTimeMs = 100L;

	/**
	 * Gets the click sound based on the configured sound string.
	 * 
	 * @return The Sound object for click sound, or null if set to "none"
	 */
	public Sound getClickSoundSound() {

		if (getClickSoundSoundStr().equalsIgnoreCase("none")) {
			return null;
		}
		try {
			return Registry.SOUNDS
					.get(NamespacedKey.minecraft(getClickSoundSoundStr().replaceAll("_", ".").toLowerCase()));
		} catch (Exception e) {
			e.printStackTrace();
			return Registry.SOUNDS.get(NamespacedKey.minecraft("ui.button.click"));
		}
	}

	/**
	 * Loads configuration options from the YML configuration file.
	 * 
	 * @param plugin The AdvancedCore plugin instance
	 */
	public void load(AdvancedCorePlugin plugin) {
		if (getYmlConfig() != null) {
			new AnnotationHandler().load(getYmlConfig().getData(), this);
			debug = DebugLevel.getDebug(debugLevelStr);
			storageType = UserStorage.value(userStorageString.toUpperCase());

			delayLoginEventMs = ParsedDuration.parse(getDelayLoginEvent(), TimeUnit.MILLISECONDS).getMillis();
			spamClickTimeMs = ParsedDuration.parse(getSpamClickTime(), TimeUnit.MILLISECONDS).getMillis();
		}
	}
}
