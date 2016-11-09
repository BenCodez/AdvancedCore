package com.Ben12345rocks.AdvancedCore.YML;

import java.io.File;

import com.Ben12345rocks.AdvancedCore.Exceptions.FileDirectoryException;

public class YMLFileHandler extends YMLFile {
	@SuppressWarnings("unused")
	private File file;

	public YMLFileHandler(File file) {
		super(file);
		this.file = file;
		if (file.isDirectory()) {
			try {
				throw new FileDirectoryException(file.getAbsolutePath()
						+ " must be a file");
			} catch (FileDirectoryException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onFileCreation() {

	}
}
