package com.Ben12345rocks.AdvancedCore.Util.Updater;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

// TODO: Auto-generated Javadoc
/**
 * The Class Updater.
 */
public class Updater {

	/**
	 * The Enum UpdateResult.
	 */
	public enum UpdateResult {

		/** The bad resourceid. */
		BAD_RESOURCEID,

		/** The disabled. */
		DISABLED,

		/** The fail noversion. */
		FAIL_NOVERSION,

		/** The fail spigot. */
		FAIL_SPIGOT,

		/** The no update. */
		NO_UPDATE,

		/** The update available. */
		UPDATE_AVAILABLE
	}

	// private final String API_KEY =
	// "98BE0FE67F88AB82B4C197FAF1DC3B69206EFDCC4D3B80FC83A00037510B99B4";

	// private HttpURLConnection connection;

	// private final String HOST = "http://www.spigotmc.org";

	/** The old version. */
	private String oldVersion;

	/** The plugin. */
	private JavaPlugin plugin;

	// private final String QUERY = "/api/general.php";

	// private final String REQUEST_METHOD = "POST";

	/** The resource id. */
	private String RESOURCE_ID = "";

	/** The result. */
	private Updater.UpdateResult result = Updater.UpdateResult.DISABLED;

	/** The version. */
	private String version;

	// private String WRITE_STRING;

	/**
	 * Instantiates a new updater.
	 *
	 * @param plugin
	 *            the plugin
	 * @param resourceId
	 *            the resource id
	 * @param disabled
	 *            the disabled
	 */
	public Updater(JavaPlugin plugin, Integer resourceId, boolean disabled) {
		RESOURCE_ID = resourceId + "";
		this.plugin = plugin;
		oldVersion = this.plugin.getDescription().getVersion();

		if (disabled) {
			result = UpdateResult.DISABLED;
			return;
		}

		// WRITE_STRING = "key=" + API_KEY + "&resource=" + RESOURCE_ID;
		run();
	}

	/**
	 * Gets the result.
	 *
	 * @return the result
	 */
	public UpdateResult getResult() {
		return result;
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	private void run() {
		try {
			HttpsURLConnection connection = (HttpsURLConnection) new URL(
					"https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID).openConnection();
			int timed_out = 1250;
			connection.setConnectTimeout(timed_out);
			connection.setReadTimeout(timed_out);
			this.version = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
			connection.disconnect();
			versionCheck();
			return;
		} catch (Exception e) {
			result = UpdateResult.FAIL_SPIGOT;
			AdvancedCoreHook.getInstance().debug(e);
		}
		result = UpdateResult.FAIL_SPIGOT;
	}

	/**
	 * Should update.
	 *
	 * @param localVersion
	 *            the local version
	 * @param remoteVersion
	 *            the remote version
	 * @return true, if successful
	 */
	public boolean shouldUpdate(String localVersion, String remoteVersion) {
		return !localVersion.equalsIgnoreCase(remoteVersion);
	}

	/**
	 * Version check.
	 */
	private void versionCheck() {
		if (shouldUpdate(oldVersion, version)) {
			result = UpdateResult.UPDATE_AVAILABLE;
		} else {
			result = UpdateResult.NO_UPDATE;
		}
	}
}
