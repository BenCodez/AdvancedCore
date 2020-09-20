package com.Ben12345rocks.AdvancedCore.Backups;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;

// TODO: Auto-generated Javadoc
/**
 * The Class Report.
 */
public class ZipCreator {

	/** The instance. */
	static ZipCreator instance = new ZipCreator();

	/**
	 * Gets the single instance of Report.
	 *
	 * @return single instance of Report
	 */
	public static ZipCreator getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new report.
	 */
	private ZipCreator() {
	}

	
	/**
	 * Adds the all files.
	 *
	 * @param dir
	 *            the dir
	 * @param fileList
	 *            the file list
	 */
	private void addAllFiles(File dir, List<File> fileList) {
		try {
			File[] files = dir.listFiles();
			for (File file : files) {
				if (file.getName() != null && !file.getName().equals("Users.db-journal")) {
					fileList.add(file);
				}
				if (file.isDirectory()) {
					plugin.debug("directory:" + file.getCanonicalPath());
					if (!file.getAbsolutePath()
							.contains(AdvancedCorePlugin.getInstance().getName() + File.separator + "Backups")
							&& !file.getAbsolutePath().contains(
									AdvancedCorePlugin.getInstance().getName() + File.separator + "Reports")) {
						addAllFiles(file, fileList);
					}

				} else {

					plugin.debug("file:" + file.getCanonicalPath());

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds the to zip.
	 *
	 * @param file
	 *            the file
	 * @param zos
	 *            the zos
	 * @throws FileNotFoundException
	 *             the file not found exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private void addToZip(File file, ZipOutputStream zos) throws FileNotFoundException, IOException {

		FileInputStream fis = new FileInputStream(file);

		String zipFilePath = file.getPath();

		plugin.extraDebug("Writing '" + zipFilePath + "' to zip file");

		ZipEntry zipEntry = new ZipEntry(zipFilePath);
		zos.putNextEntry(zipEntry);

		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zos.write(bytes, 0, length);
		}

		zos.closeEntry();
		fis.close();
	}

	public void create(File directory, File zipFileLocation) {
		if (zipFileLocation.exists()) {
			zipFileLocation.delete();
		}
		try {
			zipFileLocation.getParentFile().mkdirs();
			zipFileLocation.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		List<File> fileList = new ArrayList<File>();

		try {
			plugin.debug("---Getting references to all files in: " + directory.getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		addAllFiles(directory, fileList);
		plugin.debug("---Creating zip file");

		writeZipFile(fileList, zipFileLocation);
		plugin.debug("---Done");
	}

	/**
	 * Creates the.
	 */
	public void createReport() {
		long time = Calendar.getInstance().getTime().getTime();
		create(plugin.getDataFolder(), new File(plugin.getDataFolder(),
				"Reports" + File.separator + "Reports." + Long.toString(time) + ".zip"));
	}

	private void writeZipFile(List<File> fileList, File zipFile) {

		try {
			File fileZipFolder = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "Reports");
			if (!fileZipFolder.exists()) {
				fileZipFolder.mkdirs();
			}

			FileOutputStream fos = new FileOutputStream(zipFile);
			ZipOutputStream zos = new ZipOutputStream(fos);

			for (File file : fileList) {
				if (!file.isDirectory()) { // we only zip files, not directories
					if (!file.getAbsolutePath().equals(zipFile.getAbsolutePath())) {
						try {
							addToZip(file, zos);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
					}
				}
			}

			plugin.getLogger().info("Created zip file at " + zipFile.getAbsolutePath());

			zos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
