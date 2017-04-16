package com.Ben12345rocks.AdvancedCore.Util.Updater;

import org.bukkit.plugin.Plugin;
import org.inventivetalent.update.spiget.SpigetUpdate;
import org.inventivetalent.update.spiget.UpdateCallback;
import org.inventivetalent.update.spiget.comparator.VersionComparator;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

public class SpigetUpdater {

	private static SpigetUpdater instance = new SpigetUpdater();

	public static SpigetUpdater getInstance() {
		return instance;
	}

	private SpigetUpdater() {
	}

	/*
	 * public void download(int resourceId, String jarName) { try { download(new
	 * URL("https://api.spiget.org/v2/resources/" + resourceId + "/download"),
	 * new File(Bukkit.getServer().getUpdateFolderFile(), jarName + ".jar"));
	 * 
	 * } catch (IOException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); } }
	 * 
	 * private void download(URL url, File target) throws IOException {
	 * target.getParentFile().mkdirs(); target.createNewFile();
	 * ReadableByteChannel rbc = Channels.newChannel(url.openStream());
	 * FileOutputStream fos = new FileOutputStream(target);
	 * fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE); fos.close();
	 * rbc.close(); }
	 */

	public void download(Plugin plugin, int resourceId) {
		SpigetUpdate updater = new SpigetUpdate(plugin, resourceId);

		updater.setVersionComparator(VersionComparator.EQUAL);

		updater.checkForUpdate(new UpdateCallback() {
			@Override
			public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {

				if (hasDirectDownload) {
					if (updater.downloadUpdate()) {
						// downloaded
						plugin.getLogger().info("Version v" + newVersion + " downloaded, restart server");
					} else {
						// failed
						plugin.getLogger()
								.info("Failed to download " + newVersion + ": " + updater.getFailReason().toString());
					}
				}
			}

			@Override
			public void upToDate() {
				if (updater.downloadUpdate()) {
					// downloaded
					plugin.getLogger()
							.info("Version v" + plugin.getDescription().getVersion() + " downloaded, restart server");
				} else {
					// failed
					plugin.getLogger().info("Failed to download " + plugin.getDescription().getVersion() + ": "
							+ updater.getFailReason().toString());
				}
			}
		});
	}

	public void checkAutoDownload(Plugin plugin, int resourceId) {
		SpigetUpdate updater = new SpigetUpdate(plugin, resourceId);

		updater.setVersionComparator(VersionComparator.SEM_VER);

		updater.checkForUpdate(new UpdateCallback() {
			@Override
			public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {

				if (hasDirectDownload && AdvancedCoreHook.getInstance().isAutoDownload()) {
					if (updater.downloadUpdate()) {
						// downloaded
						plugin.getLogger().info("Version v" + newVersion + " downloaded automaticly, restart server");
					} else {
						// failed
						plugin.getLogger()
								.info("Failed to download " + newVersion + " automaticly: " + updater.getFailReason().toString());
					}
				}
			}

			@Override
			public void upToDate() {

			}
		});
	}
}
