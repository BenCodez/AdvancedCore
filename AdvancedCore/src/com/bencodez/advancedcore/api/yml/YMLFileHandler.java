package com.bencodez.advancedcore.api.yml;

import java.io.File;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.exceptions.FileDirectoryException;

public class YMLFileHandler extends YMLFile {
	@SuppressWarnings("unused")
	private File file;

	public YMLFileHandler(AdvancedCorePlugin plugin, File file) {
		super(plugin, file);
		this.file = file;
		if (file.isDirectory()) {
			try {
				throw new FileDirectoryException(file.getAbsolutePath() + " must be a file");
			} catch (FileDirectoryException e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("deprecation")
	public void header(String string) {
		getData().options().header(string);
	}

	@Override
	public void onFileCreation() {

	}
}
