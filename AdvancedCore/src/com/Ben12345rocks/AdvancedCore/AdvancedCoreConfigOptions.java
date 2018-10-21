package com.Ben12345rocks.AdvancedCore;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;

public class AdvancedCoreConfigOptions {

	private boolean debug = false;

	private boolean debugIngame = false;
	private boolean logDebugToFile = true;
	private String defaultRequestMethod = "ANVIL";
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();
	private String formatNoPerms = "&cYou do not have enough permission!";
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";
	private String permPrefix;

	private String formatChoiceRewardsPreferenceSet = "&aPreference set to %choice%";

	private boolean extraDebug = false;

	private boolean disableCheckOnWorldChange = false;
	private boolean sendScoreboards = true;

	private boolean autoKillInvs = true;

	private String prevPageTxt = "&aPrevious Page";
	private String nextPageTxt = "&aNext Page";
	private boolean checkNameMojang = false;
	private boolean alternateUUIDLookUp;

	private boolean purgeOldData = false;

	private int purgeMinimumDays = 90;

	private String formatInvFull;

	private int timeHourOffSet = 0;

	private boolean createBackups = true;

	private boolean enableJenkins;

	private ConfigurationSection configData;

	private boolean autoDownload = false;

	private UserStorage storageType = UserStorage.SQLITE;
	private int resourceId = 0;

	private boolean processRewards = true;

	/**
	 * @return the processRewards
	 */
	public boolean isProcessRewards() {
		return processRewards;
	}

	/**
	 * @param processRewards the processRewards to set
	 */
	public void setProcessRewards(boolean processRewards) {
		this.processRewards = processRewards;
	}

	public AdvancedCoreConfigOptions() {
	}

	/**
	 * @return the configData
	 */
	public ConfigurationSection getConfigData() {
		return configData;
	}

	/**
	 * @return the defaultRequestMethod
	 */
	public String getDefaultRequestMethod() {
		return defaultRequestMethod;
	}

	/**
	 * @return the disabledRequestMethods
	 */
	public ArrayList<String> getDisabledRequestMethods() {
		return disabledRequestMethods;
	}

	/**
	 * @return the formatInvFull
	 */
	public String getFormatInvFull() {
		return formatInvFull;
	}

	/**
	 * @return the formatNoPerms
	 */
	public String getFormatNoPerms() {
		return formatNoPerms;
	}

	/**
	 * @return the formatNotNumber
	 */
	public String getFormatNotNumber() {
		return formatNotNumber;
	}

	/**
	 * @return the helpLine
	 */
	public String getHelpLine() {
		return helpLine;
	}

	/**
	 * @return the nextPageTxt
	 */
	public String getNextPageTxt() {
		return nextPageTxt;
	}

	/**
	 * @return the permPrefix
	 */
	public String getPermPrefix() {
		return permPrefix;
	}

	/**
	 * @return the prevPageTxt
	 */
	public String getPrevPageTxt() {
		return prevPageTxt;
	}

	/**
	 * @return the purgeMinimumDays
	 */
	public int getPurgeMinimumDays() {
		return purgeMinimumDays;
	}

	/**
	 * @return the resourceId
	 */
	public int getResourceId() {
		return resourceId;
	}

	/**
	 * @return the storageType
	 */
	public UserStorage getStorageType() {
		return storageType;
	}

	/**
	 * @return the timeHourOffSet
	 */
	public int getTimeHourOffSet() {
		return timeHourOffSet;
	}

	/**
	 * @return the alternateUUIDLookUp
	 */
	public boolean isAlternateUUIDLookUp() {
		return alternateUUIDLookUp;
	}

	/**
	 * @return the autoDownload
	 */
	public boolean isAutoDownload() {
		return autoDownload;
	}

	/**
	 * @return the autoKillInvs
	 */
	public boolean isAutoKillInvs() {
		return autoKillInvs;
	}

	/**
	 * @return the checkNameMojang
	 */
	public boolean isCheckNameMojang() {
		return checkNameMojang;
	}

	/**
	 * @return the createBackups
	 */
	public boolean isCreateBackups() {
		return createBackups;
	}

	/**
	 * @return the debug
	 */
	public boolean isDebug() {
		return debug;
	}

	/**
	 * @return the debugIngame
	 */
	public boolean isDebugIngame() {
		return debugIngame;
	}

	/**
	 * @return the disableCheckOnWorldChange
	 */
	public boolean isDisableCheckOnWorldChange() {
		return disableCheckOnWorldChange;
	}

	/**
	 * @return the enableJenkins
	 */
	public boolean isEnableJenkins() {
		return enableJenkins;
	}

	/**
	 * @return the extraDebug
	 */
	public boolean isExtraDebug() {
		return extraDebug;
	}

	/**
	 * @return the logDebugToFile
	 */
	public boolean isLogDebugToFile() {
		return logDebugToFile;
	}

	/**
	 * @return the purgeOldData
	 */
	public boolean isPurgeOldData() {
		return purgeOldData;
	}

	/**
	 * @return the sendScoreboards
	 */
	public boolean isSendScoreboards() {
		return sendScoreboards;
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
			checkNameMojang = configData.getBoolean("CheckNameMojang", true);
			disableCheckOnWorldChange = configData.getBoolean("DisableCheckOnWorldChange");
			autoDownload = configData.getBoolean("AutoDownload", false);
			extraDebug = configData.getBoolean("ExtraDebug", false);
			storageType = UserStorage.value(configData.getString("DataStorage", "SQLITE"));

			timeHourOffSet = configData.getInt("TimeHourOffSet", 0);

			createBackups = configData.getBoolean("CreateBackups", false);

			enableJenkins = configData.getBoolean("JenkinsDownloadEnabled");
			processRewards = configData.getBoolean("ProcessRewards", true);

		}
	}

	/**
	 * @return the formatChoiceRewardsPreferenceSet
	 */
	public String getFormatChoiceRewardsPreferenceSet() {
		return formatChoiceRewardsPreferenceSet;
	}

	/**
	 * @param formatChoiceRewardsPreferenceSet
	 *            the formatChoiceRewardsPreferenceSet to set
	 */
	public void setFormatChoiceRewardsPreferenceSet(String formatChoiceRewardsPreferenceSet) {
		this.formatChoiceRewardsPreferenceSet = formatChoiceRewardsPreferenceSet;
	}

	/**
	 * @param alternateUUIDLookUp
	 *            the alternateUUIDLookUp to set
	 */
	public void setAlternateUUIDLookUp(boolean alternateUUIDLookUp) {
		this.alternateUUIDLookUp = alternateUUIDLookUp;
	}

	/**
	 * @param autoDownload
	 *            the autoDownload to set
	 */
	public void setAutoDownload(boolean autoDownload) {
		this.autoDownload = autoDownload;
	}

	/**
	 * @param autoKillInvs
	 *            the autoKillInvs to set
	 */
	public void setAutoKillInvs(boolean autoKillInvs) {
		this.autoKillInvs = autoKillInvs;
	}

	/**
	 * @param checkNameMojang
	 *            the checkNameMojang to set
	 */
	public void setCheckNameMojang(boolean checkNameMojang) {
		this.checkNameMojang = checkNameMojang;
	}

	/**
	 * @param configData
	 *            the configData to set
	 */
	public void setConfigData(ConfigurationSection configData) {
		this.configData = configData;
	}

	/**
	 * @param createBackups
	 *            the createBackups to set
	 */
	public void setCreateBackups(boolean createBackups) {
		this.createBackups = createBackups;
	}

	/**
	 * @param debug
	 *            the debug to set
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * @param debugIngame
	 *            the debugIngame to set
	 */
	public void setDebugIngame(boolean debugIngame) {
		this.debugIngame = debugIngame;
	}

	/**
	 * @param defaultRequestMethod
	 *            the defaultRequestMethod to set
	 */
	public void setDefaultRequestMethod(String defaultRequestMethod) {
		this.defaultRequestMethod = defaultRequestMethod;
	}

	/**
	 * @param disableCheckOnWorldChange
	 *            the disableCheckOnWorldChange to set
	 */
	public void setDisableCheckOnWorldChange(boolean disableCheckOnWorldChange) {
		this.disableCheckOnWorldChange = disableCheckOnWorldChange;
	}

	/**
	 * @param disabledRequestMethods
	 *            the disabledRequestMethods to set
	 */
	public void setDisabledRequestMethods(ArrayList<String> disabledRequestMethods) {
		this.disabledRequestMethods = disabledRequestMethods;
	}

	/**
	 * @param enableJenkins
	 *            the enableJenkins to set
	 */
	public void setEnableJenkins(boolean enableJenkins) {
		this.enableJenkins = enableJenkins;
	}

	/**
	 * @param extraDebug
	 *            the extraDebug to set
	 */
	public void setExtraDebug(boolean extraDebug) {
		this.extraDebug = extraDebug;
	}

	/**
	 * @param formatInvFull
	 *            the formatInvFull to set
	 */
	public void setFormatInvFull(String formatInvFull) {
		this.formatInvFull = formatInvFull;
	}

	/**
	 * @param formatNoPerms
	 *            the formatNoPerms to set
	 */
	public void setFormatNoPerms(String formatNoPerms) {
		this.formatNoPerms = formatNoPerms;
	}

	/**
	 * @param formatNotNumber
	 *            the formatNotNumber to set
	 */
	public void setFormatNotNumber(String formatNotNumber) {
		this.formatNotNumber = formatNotNumber;
	}

	/**
	 * @param helpLine
	 *            the helpLine to set
	 */
	public void setHelpLine(String helpLine) {
		this.helpLine = helpLine;
	}

	/**
	 * @param logDebugToFile
	 *            the logDebugToFile to set
	 */
	public void setLogDebugToFile(boolean logDebugToFile) {
		this.logDebugToFile = logDebugToFile;
	}

	/**
	 * @param nextPageTxt
	 *            the nextPageTxt to set
	 */
	public void setNextPageTxt(String nextPageTxt) {
		this.nextPageTxt = nextPageTxt;
	}

	/**
	 * @param permPrefix
	 *            the permPrefix to set
	 */
	public void setPermPrefix(String permPrefix) {
		this.permPrefix = permPrefix;
	}

	/**
	 * @param prevPageTxt
	 *            the prevPageTxt to set
	 */
	public void setPrevPageTxt(String prevPageTxt) {
		this.prevPageTxt = prevPageTxt;
	}

	/**
	 * @param purgeMinimumDays
	 *            the purgeMinimumDays to set
	 */
	public void setPurgeMinimumDays(int purgeMinimumDays) {
		this.purgeMinimumDays = purgeMinimumDays;
	}

	/**
	 * @param purgeOldData
	 *            the purgeOldData to set
	 */
	public void setPurgeOldData(boolean purgeOldData) {
		this.purgeOldData = purgeOldData;
	}

	/**
	 * @param resourceId
	 *            the resourceId to set
	 */
	public void setResourceId(int resourceId) {
		this.resourceId = resourceId;
	}

	/**
	 * @param sendScoreboards
	 *            the sendScoreboards to set
	 */
	public void setSendScoreboards(boolean sendScoreboards) {
		this.sendScoreboards = sendScoreboards;
	}

	/**
	 * @param storageType
	 *            the storageType to set
	 */
	public void setStorageType(UserStorage storageType) {
		this.storageType = storageType;
	}

	/**
	 * @param timeHourOffSet
	 *            the timeHourOffSet to set
	 */
	public void setTimeHourOffSet(int timeHourOffSet) {
		this.timeHourOffSet = timeHourOffSet;
	}

}
