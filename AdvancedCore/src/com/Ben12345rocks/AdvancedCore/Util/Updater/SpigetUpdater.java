package com.Ben12345rocks.AdvancedCore.Util.Updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.bukkit.Bukkit;

public class SpigetUpdater {

	private static SpigetUpdater instance = new SpigetUpdater();

	public static SpigetUpdater getInstance() {
		return instance;
	}

	private SpigetUpdater() {
	}

	public void download(int resourceId, String jarName) {
		try {
			download(new URL("https://api.spiget.org/v2/resources/" + resourceId + "/download"),
					new File(Bukkit.getServer().getUpdateFolderFile(),
							jarName + ".jar"));
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void download(URL url, File target) throws IOException {
		/*Path targetPath = target.toPath();
		Files.copy(url.openStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);*/
		target.getParentFile().mkdirs();
		target.createNewFile();
        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fos = new FileOutputStream(target);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.close();
        rbc.close();
	}

}
