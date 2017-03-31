package com.Ben12345rocks.AdvancedCore.Util.Updater;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

public class SpigetUpdater {

	private static SpigetUpdater instance = new SpigetUpdater();

	public static SpigetUpdater getInstance() {
		return instance;
	}

	private SpigetUpdater() {
	}

	public void download(int resourceId, String jarName) {
		try {
			download(new URL("https://api.spiget.org/v2/resources/" + resourceId + "/versions/latest/download"),
					new File(AdvancedCoreHook.getInstance().getPlugin().getServer().getUpdateFolder(),
							jarName + ".jar"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void download(URL url, File target) throws IOException {
		Path targetPath = target.toPath();
		Files.copy(url.openStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

}
