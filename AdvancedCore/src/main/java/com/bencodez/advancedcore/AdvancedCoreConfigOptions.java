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
	 * @return the YML configuration file handler
	 * @param ymlConfig the YML configuration file handler
	 */
	@Getter
	@Setter
	private YMLConfig ymlConfig;

	/**
	 * Whether rewards are currently paused.
	 * @return true if rewards are paused, false otherwise
	 * @param pauseRewards true to pause rewards, false otherwise
	 */
	@Getter
	@Setter
	private boolean pauseRewards = false;

	/**
	 * The pitch of the click sound (0.5 to 2.0).
	 * @return the pitch of the click sound
	 * @param clickSoundPitch the pitch of the click sound
	 */
	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Pitch")
	private double clickSoundPitch = 1;
	/**
	 * The sound string identifier for the click sound.
	 * @return the sound string identifier for the click sound
	 * @param clickSoundSoundStr the sound string identifier for the click sound
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "ClickSound.Sound")
	private String clickSoundSoundStr = "ui.button.click";

	/**
	 * The volume of the click sound (0.0 to 1.0).
	 * @return the volume of the click sound
	 * @param clickSoundVolume the volume of the click sound
	 */
	@Getter
	@Setter
	@ConfigDataDouble(path = "ClickSound.Volume")
	private double clickSoundVolume = 1;

	/**
	 * Whether to create backup files of configuration.
	 * @return true if backups should be created, false otherwise
	 * @param createBackups true to create backups, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "CreateBackups")
	private boolean createBackups = false;

	@ConfigDataString(path = "DebugLevel", options = { "NONE", "INFO", "EXTRA", "DEV" })
	private String debugLevelStr = "NONE";

	/**
	 * The current debug level for logging.
	 * @return the current debug level
	 * @param debug the debug level to set
	 */
	@Getter
	@Setter
	private DebugLevel debug = DebugLevel.NONE;

	/**
	 * The default request method for user input (ANVIL, CHAT, etc).
	 * @return the default request method
	 * @param defaultRequestMethod the default request method to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "RequestAPI.DefaultMethod")
	private String defaultRequestMethod = "ANVIL";

	/**
	 * Whether to disable reward checks on world change events.
	 * @return true if checks are disabled, false otherwise
	 * @param disableCheckOnWorldChange true to disable checks, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableCheckOnWorldChange")
	private boolean disableCheckOnWorldChange = false;
	/**
	 * List of disabled request methods.
	 * @return the list of disabled request methods
	 * @param disabledRequestMethods the list of disabled request methods to set
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "RequestAPI.DisabledMethods")
	private ArrayList<String> disabledRequestMethods = new ArrayList<>();

	/**
	 * Whether to drop items on ground when inventory is full.
	 * @return true if items should drop, false otherwise
	 * @param dropOnFullInv true to drop items, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DropOnFullInv")
	private boolean dropOnFullInv = true;

	/**
	 * Whether Jenkins download functionality is enabled.
	 * @return true if Jenkins is enabled, false otherwise
	 * @param enableJenkins true to enable Jenkins, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "JenkinsDownloadEnabled")
	private boolean enableJenkins = false;

	/**
	 * Format string for choice rewards preference set message.
	 * @return the format string for choice rewards preference set message
	 * @param formatChoiceRewardsPreferenceSet the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.ChoiceRewards.PreferenceSet")
	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	/**
	 * Format string for inventory full message.
	 * @return the format string for inventory full message
	 * @param formatInvFull the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.InvFull")
	private String formatInvFull = "&cInventory full";

	/**
	 * Format string for no permissions message.
	 * @return the format string for no permissions message
	 * @param formatNoPerms the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.NoPerms")
	private String formatNoPerms = "&cYou do not have enough permission!";

	/**
	 * Format string for not a number error message.
	 * @return the format string for not a number error message
	 * @param formatNotNumber the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.NotNumber")
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";

	/**
	 * Time format string for displaying reward times.
	 * @return the time format string for displaying reward times
	 * @param formatRewardTimeFormat the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.RewardTimeFormat")
	private String formatRewardTimeFormat = "EEE, d MMM yyyy HH:mm";

	/**
	 * Prefix for Bedrock Edition player names.
	 * @return the prefix for Bedrock Edition player names
	 * @param bedrockPlayerPrefix the prefix to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "BedrockPlayerPrefix")
	private String bedrockPlayerPrefix = ".";

	/**
	 * Whether the server is running in online mode.
	 * @return true if server is in online mode, false otherwise
	 * @param onlineMode true for online mode, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "OnlineMode")
	private boolean onlineMode = true;

	/**
	 * Format string for help command lines.
	 * @return the format string for help command lines
	 * @param helpLine the format string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "Format.HelpLine")
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";

	/**
	 * Whether to load default rewards on startup.
	 * @return true if default rewards should be loaded, false otherwise
	 * @param loadDefaultRewards true to load default rewards, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean loadDefaultRewards = true;

	/**
	 * Whether to check multiple permission nodes for rewards.
	 * @return true if multiple permission checks are enabled, false otherwise
	 * @param multiplePermissionChecks true to enable multiple permission checks, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "")
	private boolean multiplePermissionChecks = false;

	/**
	 * Maximum length for item lore lines before wrapping.
	 * @return the maximum length for item lore lines
	 * @param newLoreLength the maximum length to set
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "")
	private int newLoreLength = 30;

	/**
	 * Configuration section for the next page button in GUIs.
	 * @return the configuration section for the next page button
	 * @param nextItem the configuration section to set
	 */
	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.NextItem")
	private ConfigurationSection nextItem;

	/**
	 * Permission prefix for plugin permissions.
	 * @return the permission prefix
	 * @param permPrefix the permission prefix to set
	 */
	@Getter
	@Setter
	private String permPrefix;

	/**
	 * Whether rewards are specific to each server in a network.
	 * @return true if rewards are per-server, false otherwise
	 * @param perServerRewards true for per-server rewards, false otherwise
	 */
	@Getter
	@Setter
	private boolean perServerRewards = false;

	/**
	 * Configuration section for the previous page button in GUIs.
	 * @return the configuration section for the previous page button
	 * @param prevItem the configuration section to set
	 */
	@Getter
	@Setter
	@ConfigDataConfigurationSection(path = "Format.PrevItem")
	private ConfigurationSection prevItem;

	/**
	 * Whether to process rewards when giving them to players.
	 * @return true if rewards should be processed, false otherwise
	 * @param processRewards true to process rewards, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "ProcessRewards")
	private boolean processRewards = true;

	/**
	 * Minimum number of days before player data can be purged.
	 * @return the minimum number of days before purge
	 * @param purgeMinimumDays the minimum number of days to set
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "PurgeMin")
	private int purgeMinimumDays = 90;

	/**
	 * Whether to automatically purge old player data.
	 * @return true if old data should be purged, false otherwise
	 * @param purgeOldData true to purge old data, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "PurgeOldData")
	private boolean purgeOldData = false;

	/**
	 * Whether to purge old data immediately on server startup.
	 * @return true if data should be purged on startup, false otherwise
	 * @param purgeDataOnStartup true to purge data on startup, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "PurgeDataOnStartup")
	private boolean purgeDataOnStartup = false;

	/**
	 * API URL for fetching player skull profiles.
	 * @return the API URL for fetching player skull profiles
	 * @param SkullProfileAPIURL the API URL to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SkullProfileAPIURL")
	private String SkullProfileAPIURL = "";

	/**
	 * Whether JavaScript execution is disabled.
	 * @return true if JavaScript is disabled, false otherwise
	 * @param disableJavascript true to disable JavaScript, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "DisableJavascript")
	private boolean disableJavascript = false;

	/**
	 * Whether the JavaScript command is enabled.
	 * @return true if JavaScript command is enabled, false otherwise
	 * @param enableJavascriptCommand true to enable JavaScript command, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "EnableJavascriptCommand")
	private boolean enableJavascriptCommand = false;

	/**
	 * Spigot resource ID for update checking.
	 * @return the Spigot resource ID
	 * @param resourceId the Spigot resource ID to set
	 */
	@Getter
	@Setter
	private int resourceId = 0;

	/**
	 * Whether to send scoreboard packets to players.
	 * @return true if scoreboards should be sent, false otherwise
	 * @param sendScoreboards true to send scoreboards, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "SendScoreboards")
	private boolean sendScoreboards = true;

	/**
	 * Server name identifier for multi-server setups.
	 * @return the server name identifier
	 * @param server the server name to set
	 */
	@Setter
	@Getter
	@ConfigDataString(path = "Server")
	private String server = "";

	/**
	 * Message to display when a player spam-clicks.
	 * @return the spam click message
	 * @param spamClickMessage the spam click message to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SpamClickMessage")
	private String spamClickMessage = "";

	/**
	 * Time duration string for spam click detection.
	 * @return the time duration string for spam click detection
	 * @param spamClickTime the spam click time to set
	 */
	@Setter
	@Getter
	@ConfigDataString(path = "SpamClickTime")
	private String spamClickTime = "100ms";

	@ConfigDataString(path = "DataStorage")
	private String userStorageString = "SQLITE";

	/**
	 * The storage type for user data (SQLITE, MYSQL, etc).
	 * @return the storage type for user data
	 * @param storageType the storage type to set
	 */
	@Getter
	@Setter
	private UserStorage storageType = UserStorage.SQLITE;

	/**
	 * Hour offset for time-based features.
	 * @return the hour offset for time-based features
	 * @param timeHourOffSet the hour offset to set
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "TimeHourOffSet")
	private int timeHourOffSet = 0;

	/**
	 * Week offset for time-based features.
	 * @return the week offset for time-based features
	 * @param timeWeekOffSet the week offset to set
	 */
	@Getter
	@Setter
	@ConfigDataInt(path = "TimeWeekOffSet")
	private int timeWeekOffSet = 0;

	/**
	 * Whether to bypass fail-safe checks for time changes.
	 * @return true if fail-safe bypass is enabled, false otherwise
	 * @param timeChangeFailSafeBypass true to bypass fail-safe checks, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "TimeChangeFailSafeBypass")
	private boolean timeChangeFailSafeBypass = false;

	/**
	 * Time zone string for time-based features.
	 * @return the time zone string for time-based features
	 * @param timeZone the time zone string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "TimeZone")
	private String timeZone = "";

	/**
	 * Whether to treat vanished players as offline.
	 * @return true if vanished players are treated as offline, false otherwise
	 * @param treatVanishAsOffline true to treat vanished players as offline, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "TreatVanishAsOffline")
	private boolean treatVanishAsOffline = true;

	/**
	 * Whether to use Vault for permission checks.
	 * @return true if Vault permissions are used, false otherwise
	 * @param useVaultPermissions true to use Vault permissions, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "UseVaultPermissions")
	private boolean useVaultPermissions = false;

	/**
	 * Whether to wait until players are logged in before processing rewards.
	 * @return true if waiting for login is enabled, false otherwise
	 * @param waitUntilLoggedIn true to wait until logged in, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "WaitUntilLoggedIn")
	private boolean waitUntilLoggedIn;

	/**
	 * Delay duration string before processing login events.
	 * @return the delay duration string before processing login events
	 * @param delayLoginEvent the delay duration string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "DelayLoginEvent")
	private String delayLoginEvent = "0s";

	/**
	 * Delay duration string before loading player skulls.
	 * @return the delay duration string before loading player skulls
	 * @param SkullLoadDelay the delay duration string to set
	 */
	@Getter
	@Setter
	@ConfigDataString(path = "SkullLoadDelay")
	private String SkullLoadDelay = "4s";

	/**
	 * List of worlds where broadcasts are disabled.
	 * @return the list of worlds where broadcasts are disabled
	 * @param broadcastBlacklist the list of worlds to set
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "BroadcastBlacklist")
	private ArrayList<String> broadcastBlacklist = new ArrayList<>();

	/**
	 * Whether to close GUIs when shift-clicking items.
	 * @return true if GUIs should close on shift-click, false otherwise
	 * @param closeGUIOnShiftClick true to close GUIs on shift-click, false otherwise
	 */
	@Getter
	@Setter
	@ConfigDataBoolean(path = "CloseGUIOnShiftClick")
	private boolean closeGUIOnShiftClick = false;

	/**
	 * List of worlds where default rewards are enabled.
	 * @return the list of worlds where default rewards are enabled
	 * @param defaultRewardWorlds the list of worlds to set
	 */
	@Getter
	@Setter
	@ConfigDataListString(path = "DefaultRewardWorlds")
	private ArrayList<String> defaultRewardWorlds = new ArrayList<>();

	/**
	 * List of worlds where default rewards are disabled.
	 * @return the list of worlds where default rewards are disabled
	 * @param defaultRewardBlackListedWorlds the list of worlds to set
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
	 * @return the delay in milliseconds before processing login events
	 */
	@Getter
	private long delayLoginEventMs = 0L;
	/**
	 * Time in milliseconds for spam click detection.
	 * @return the time in milliseconds for spam click detection
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
