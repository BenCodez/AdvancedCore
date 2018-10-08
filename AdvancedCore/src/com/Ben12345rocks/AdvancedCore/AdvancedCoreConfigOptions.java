package com.Ben12345rocks.AdvancedCore;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;

public class AdvancedCoreConfigOptions {

	public AdvancedCoreConfigOptions() {
	}

	private boolean debug = false;
	private boolean debugIngame = false;
	private boolean logDebugToFile = true;
	private String defaultRequestMethod = "ANVIL";
	private ArrayList<String> disabledRequestMethods = new ArrayList<String>();
	private String formatNoPerms = "&cYou do not have enough permission!";
	private String formatNotNumber = "&cError on &6%arg%&c, number expected!";
	private String helpLine = "&3&l%Command% - &3%HelpMessage%";

	private String permPrefix;

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

	/**
	 * @return the storageType
	 */
	public UserStorage getStorageType() {
		return storageType;
	}

	/**
	 * @param storageType
	 *            the storageType to set
	 */
	public void setStorageType(UserStorage storageType) {
		this.storageType = storageType;
	}

	private int resourceId = 0;

	/**
	 * @return the resourceId
	 */
	public int getResourceId() {
		return resourceId;
	}

	/**
	 * @param resourceId the resourceId to set
	 */
	public void setResourceId(int resourceId) {
		this.resourceId = resourceId;
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

		}
	}

	/**
	 * @return the debug
	 */
	public boolean isDebug() {
		return debug;
	}

	/**
	 * @param debug
	 *            the debug to set
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * @return the debugIngame
	 */
	public boolean isDebugIngame() {
		return debugIngame;
	}

	/**
	 * @param debugIngame
	 *            the debugIngame to set
	 */
	public void setDebugIngame(boolean debugIngame) {
		this.debugIngame = debugIngame;
	}

	/**
	 * @return the logDebugToFile
	 */
	public boolean isLogDebugToFile() {
		return logDebugToFile;
	}

	/**
	 * @param logDebugToFile
	 *            the logDebugToFile to set
	 */
	public void setLogDebugToFile(boolean logDebugToFile) {
		this.logDebugToFile = logDebugToFile;
	}

	/**
	 * @return the defaultRequestMethod
	 */
	public String getDefaultRequestMethod() {
		return defaultRequestMethod;
	}

	/**
	 * @param defaultRequestMethod
	 *            the defaultRequestMethod to set
	 */
	public void setDefaultRequestMethod(String defaultRequestMethod) {
		this.defaultRequestMethod = defaultRequestMethod;
	}

	/**
	 * @return the disabledRequestMethods
	 */
	public ArrayList<String> getDisabledRequestMethods() {
		return disabledRequestMethods;
	}

	/**
	 * @param disabledRequestMethods
	 *            the disabledRequestMethods to set
	 */
	public void setDisabledRequestMethods(ArrayList<String> disabledRequestMethods) {
		this.disabledRequestMethods = disabledRequestMethods;
	}

	/**
	 * @return the formatNoPerms
	 */
	public String getFormatNoPerms() {
		return formatNoPerms;
	}

	/**
	 * @param formatNoPerms
	 *            the formatNoPerms to set
	 */
	public void setFormatNoPerms(String formatNoPerms) {
		this.formatNoPerms = formatNoPerms;
	}

	/**
	 * @return the formatNotNumber
	 */
	public String getFormatNotNumber() {
		return formatNotNumber;
	}

	/**
	 * @param formatNotNumber
	 *            the formatNotNumber to set
	 */
	public void setFormatNotNumber(String formatNotNumber) {
		this.formatNotNumber = formatNotNumber;
	}

	/**
	 * @return the helpLine
	 */
	public String getHelpLine() {
		return helpLine;
	}

	/**
	 * @param helpLine
	 *            the helpLine to set
	 */
	public void setHelpLine(String helpLine) {
		this.helpLine = helpLine;
	}

	/**
	 * @return the permPrefix
	 */
	public String getPermPrefix() {
		return permPrefix;
	}

	/**
	 * @param permPrefix
	 *            the permPrefix to set
	 */
	public void setPermPrefix(String permPrefix) {
		this.permPrefix = permPrefix;
	}

	/**
	 * @return the extraDebug
	 */
	public boolean isExtraDebug() {
		return extraDebug;
	}

	/**
	 * @param extraDebug
	 *            the extraDebug to set
	 */
	public void setExtraDebug(boolean extraDebug) {
		this.extraDebug = extraDebug;
	}

	/**
	 * @return the disableCheckOnWorldChange
	 */
	public boolean isDisableCheckOnWorldChange() {
		return disableCheckOnWorldChange;
	}

	/**
	 * @param disableCheckOnWorldChange
	 *            the disableCheckOnWorldChange to set
	 */
	public void setDisableCheckOnWorldChange(boolean disableCheckOnWorldChange) {
		this.disableCheckOnWorldChange = disableCheckOnWorldChange;
	}

	/**
	 * @return the sendScoreboards
	 */
	public boolean isSendScoreboards() {
		return sendScoreboards;
	}

	/**
	 * @param sendScoreboards
	 *            the sendScoreboards to set
	 */
	public void setSendScoreboards(boolean sendScoreboards) {
		this.sendScoreboards = sendScoreboards;
	}

	/**
	 * @return the autoKillInvs
	 */
	public boolean isAutoKillInvs() {
		return autoKillInvs;
	}

	/**
	 * @param autoKillInvs
	 *            the autoKillInvs to set
	 */
	public void setAutoKillInvs(boolean autoKillInvs) {
		this.autoKillInvs = autoKillInvs;
	}

	/**
	 * @return the prevPageTxt
	 */
	public String getPrevPageTxt() {
		return prevPageTxt;
	}

	/**
	 * @param prevPageTxt
	 *            the prevPageTxt to set
	 */
	public void setPrevPageTxt(String prevPageTxt) {
		this.prevPageTxt = prevPageTxt;
	}

	/**
	 * @return the nextPageTxt
	 */
	public String getNextPageTxt() {
		return nextPageTxt;
	}

	/**
	 * @param nextPageTxt
	 *            the nextPageTxt to set
	 */
	public void setNextPageTxt(String nextPageTxt) {
		this.nextPageTxt = nextPageTxt;
	}

	/**
	 * @return the checkNameMojang
	 */
	public boolean isCheckNameMojang() {
		return checkNameMojang;
	}

	/**
	 * @param checkNameMojang
	 *            the checkNameMojang to set
	 */
	public void setCheckNameMojang(boolean checkNameMojang) {
		this.checkNameMojang = checkNameMojang;
	}

	/**
	 * @return the alternateUUIDLookUp
	 */
	public boolean isAlternateUUIDLookUp() {
		return alternateUUIDLookUp;
	}

	/**
	 * @param alternateUUIDLookUp
	 *            the alternateUUIDLookUp to set
	 */
	public void setAlternateUUIDLookUp(boolean alternateUUIDLookUp) {
		this.alternateUUIDLookUp = alternateUUIDLookUp;
	}

	/**
	 * @return the purgeOldData
	 */
	public boolean isPurgeOldData() {
		return purgeOldData;
	}

	/**
	 * @param purgeOldData
	 *            the purgeOldData to set
	 */
	public void setPurgeOldData(boolean purgeOldData) {
		this.purgeOldData = purgeOldData;
	}

	/**
	 * @return the purgeMinimumDays
	 */
	public int getPurgeMinimumDays() {
		return purgeMinimumDays;
	}

	/**
	 * @param purgeMinimumDays
	 *            the purgeMinimumDays to set
	 */
	public void setPurgeMinimumDays(int purgeMinimumDays) {
		this.purgeMinimumDays = purgeMinimumDays;
	}

	/**
	 * @return the formatInvFull
	 */
	public String getFormatInvFull() {
		return formatInvFull;
	}

	/**
	 * @param formatInvFull
	 *            the formatInvFull to set
	 */
	public void setFormatInvFull(String formatInvFull) {
		this.formatInvFull = formatInvFull;
	}

	/**
	 * @return the timeHourOffSet
	 */
	public int getTimeHourOffSet() {
		return timeHourOffSet;
	}

	/**
	 * @param timeHourOffSet
	 *            the timeHourOffSet to set
	 */
	public void setTimeHourOffSet(int timeHourOffSet) {
		this.timeHourOffSet = timeHourOffSet;
	}

	/**
	 * @return the createBackups
	 */
	public boolean isCreateBackups() {
		return createBackups;
	}

	/**
	 * @param createBackups
	 *            the createBackups to set
	 */
	public void setCreateBackups(boolean createBackups) {
		this.createBackups = createBackups;
	}

	/**
	 * @return the enableJenkins
	 */
	public boolean isEnableJenkins() {
		return enableJenkins;
	}

	/**
	 * @param enableJenkins
	 *            the enableJenkins to set
	 */
	public void setEnableJenkins(boolean enableJenkins) {
		this.enableJenkins = enableJenkins;
	}

	/**
	 * @return the configData
	 */
	public ConfigurationSection getConfigData() {
		return configData;
	}

	/**
	 * @param configData
	 *            the configData to set
	 */
	public void setConfigData(ConfigurationSection configData) {
		this.configData = configData;
	}

	/**
	 * @return the autoDownload
	 */
	public boolean isAutoDownload() {
		return autoDownload;
	}

	/**
	 * @param autoDownload
	 *            the autoDownload to set
	 */
	public void setAutoDownload(boolean autoDownload) {
		this.autoDownload = autoDownload;
	}

}
