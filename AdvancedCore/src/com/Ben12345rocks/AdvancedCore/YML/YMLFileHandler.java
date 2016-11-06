package com.Ben12345rocks.AdvancedCore.YML;

import java.io.File;
import java.io.IOException;

import com.Ben12345rocks.AdvancedCore.Exceptions.FileDirectoryException;

public class YMLFileHandler extends YMLFile {
	private File file;
	private boolean create;

	public YMLFileHandler(File file, boolean create) {
		super(file);
		this.file = file;
		this.create = create;
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
		if (create) {
			file.getParentFile().mkdirs();
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
