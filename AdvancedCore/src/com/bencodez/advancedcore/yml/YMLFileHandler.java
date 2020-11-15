package com.bencodez.advancedcore.yml;

import java.io.File;

import com.bencodez.advancedcore.api.exceptions.FileDirectoryException;

public class YMLFileHandler extends YMLFile {
	@SuppressWarnings("unused")
	private File file;

	public YMLFileHandler(File file) {
		super(file);
		this.file = file;
		if (file.isDirectory()) {
			try {
				throw new FileDirectoryException(file.getAbsolutePath() + " must be a file");
			} catch (FileDirectoryException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onFileCreation() {

	}
}
