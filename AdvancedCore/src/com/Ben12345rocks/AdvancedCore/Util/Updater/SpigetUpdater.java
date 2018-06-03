package com.Ben12345rocks.AdvancedCore.Util.Updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

public class SpigetUpdater {

	private static SpigetUpdater instance = new SpigetUpdater();

	public static SpigetUpdater getInstance() {
		return instance;
	}

	private SpigetUpdater() {
	}

	public void checkAutoDownload(JavaPlugin plugin, int resourceId) {
		Updater updater = new Updater(plugin, resourceId, !AdvancedCoreHook.getInstance().isAutoDownload());
		switch (updater.getResult()) {
		case UPDATE_AVAILABLE:
			plugin.getLogger()
					.info("Downloaded jar automaticly, restart to update. Note: Updates take 30-40 minutes to load");
			download(plugin, resourceId);
			break;
		default:
			break;
		}
	}

	public void download(Plugin plugin, int resourceId) {
		try {
			download(new URL("https://api.spiget.org/v2/resources/" + resourceId + "/download"),
					new File(Bukkit.getServer().getUpdateFolderFile(), plugin.getDescription().getName() + ".jar"));
		} catch (IOException e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void download(URL url, File target) throws IOException {
		target.getParentFile().mkdirs();
		target.createNewFile();
		ReadableByteChannel rbc = Channels.newChannel(url.openStream());
		FileOutputStream fos = new FileOutputStream(target);
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		fos.close();
		rbc.close();
	}

	public void downloadFromJenkins(String site, String projectName) {
		try {
			download(
					new URL("http://" + site + "/job/" + projectName + "/lastSuccessfulBuild/artifact/" + projectName
							+ "/target/" + projectName + ".jar"),
					new File(Bukkit.getServer().getUpdateFolderFile(),
							AdvancedCoreHook.getInstance().getPlugin().getDescription().getName() + ".jar"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		// String site =
		// "http://ben12345rocks.com/job/AylaChat/lastSuccessfulBuild/artifact/AylaChat/target/AylaChat.jar";
	}
}
