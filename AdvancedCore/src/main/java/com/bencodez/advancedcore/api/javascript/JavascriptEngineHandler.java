package com.bencodez.advancedcore.api.javascript;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import lombok.Setter;

/**
 * Handler for Javascript engine creation across different Java versions.
 */
public class JavascriptEngineHandler {

	private static final String NASHORN_FACTORY_CLASS = "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory";
	private static final String PRIMARY_NASHORN_VERSION = "15.7";
	private static final String FALLBACK_NASHORN_VERSION = "15.4";
	private static final String ASM_VERSION_FOR_PRIMARY = "9.8";
	private static final String ASM_VERSION_FOR_FALLBACK = "7.3.1";
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 30000;

	@Getter
	private static final JavascriptEngineHandler instance = new JavascriptEngineHandler();

	@Getter
	@Setter
	private JavaPlugin plugin;

	@Getter
	@Setter
	private boolean enabled = true;

	@Getter
	@Setter
	private boolean autoDownload = true;

	@Getter
	@Setter
	private URLClassLoader nashornClassLoader;

	@Getter
	@Setter
	private Class<?> factory;

	@Getter
	@Setter
	private Method methodToUse;

	@Getter
	@Setter
	private String loadedVersion;

	/**
	 * Creates a new Javascript engine handler.
	 */
	public JavascriptEngineHandler() {
	}

	/**
	 * Initializes the Javascript engine handler.
	 *
	 * @param plugin AdvancedCore plugin instance
	 * @param enabled true if Javascript support is enabled
	 * @param autoDownload true if missing Nashorn dependencies should be downloaded
	 */
	public void init(JavaPlugin plugin, boolean enabled, boolean autoDownload) {
		this.plugin = plugin;
		this.enabled = enabled;
		this.autoDownload = autoDownload;
	}

	/**
	 * Gets a Javascript script engine.
	 *
	 * @return the script engine
	 */
	public ScriptEngine getJSScriptEngine() {
		if (!enabled) {
			return null;
		}

		ScriptEngine engine = getServerProvidedEngine();

		if (engine != null) {
			return engine;
		}

		engine = getDownloadedEngine(PRIMARY_NASHORN_VERSION, ASM_VERSION_FOR_PRIMARY);

		if (engine != null) {
			return engine;
		}

		logWarning("Failed to load Nashorn " + PRIMARY_NASHORN_VERSION + ", trying fallback "
				+ FALLBACK_NASHORN_VERSION + ".");

		engine = getDownloadedEngine(FALLBACK_NASHORN_VERSION, ASM_VERSION_FOR_FALLBACK);

		if (engine == null) {
			logWarning("No Javascript engine is available. Javascript features will be disabled.");
		}

		return engine;
	}

	/**
	 * Gets a Javascript engine already available to the JVM or server classpath.
	 *
	 * @return the script engine, or null if none exists
	 */
	private ScriptEngine getServerProvidedEngine() {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");

		if (engine != null) {
			return engine;
		}

		engine = new ScriptEngineManager().getEngineByName("JavaScript");

		if (engine != null) {
			return engine;
		}

		engine = new ScriptEngineManager().getEngineByName("nashorn");

		if (engine != null) {
			return engine;
		}

		try {
			Class<?> foundFactory = Class.forName(NASHORN_FACTORY_CLASS);
			Method foundMethod = getScriptEngineMethod(foundFactory);

			if (foundMethod == null) {
				return null;
			}

			return (ScriptEngine) foundMethod.invoke(foundFactory.getDeclaredConstructor().newInstance());
		} catch (ClassNotFoundException ignored) {
			return null;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| InstantiationException | NoSuchMethodException e) {
			logWarning("Failed to create server-provided Nashorn engine: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Gets a downloaded Nashorn engine.
	 *
	 * @param nashornVersion Nashorn version
	 * @param asmVersion ASM version
	 * @return the script engine, or null if unavailable
	 */
	private ScriptEngine getDownloadedEngine(String nashornVersion, String asmVersion) {
		if (plugin == null) {
			logWarning("Javascript engine handler has not been initialized with a plugin instance.");
			return null;
		}

		try {
			List<Path> jars = getOrDownloadJars(nashornVersion, asmVersion);

			if (jars.isEmpty()) {
				return null;
			}

			URLClassLoader loader = createClassLoader(jars);
			Class<?> foundFactory = Class.forName(NASHORN_FACTORY_CLASS, true, loader);
			Method foundMethod = getScriptEngineMethod(foundFactory);

			if (foundMethod == null) {
				closeQuietly(loader);
				logWarning("Nashorn " + nashornVersion + " was loaded, but no getScriptEngine method was found.");
				return null;
			}

			ScriptEngine engine = (ScriptEngine) foundMethod.invoke(foundFactory.getDeclaredConstructor().newInstance());

			if (engine == null) {
				closeQuietly(loader);
				logWarning("Nashorn " + nashornVersion + " returned a null script engine.");
				return null;
			}

			closeExistingClassLoader();

			nashornClassLoader = loader;
			factory = foundFactory;
			methodToUse = foundMethod;
			loadedVersion = nashornVersion;

			logInfo("Loaded optional Nashorn Javascript engine " + nashornVersion + ".");
			return engine;
		} catch (Exception e) {
			logWarning("Failed to load Nashorn " + nashornVersion + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Gets or downloads all jars for a Nashorn version.
	 *
	 * @param nashornVersion Nashorn version
	 * @param asmVersion ASM version
	 * @return jar paths
	 * @throws IOException if jars cannot be prepared
	 */
	private List<Path> getOrDownloadJars(String nashornVersion, String asmVersion) throws IOException {
		Path libraryFolder = getLibraryFolder();
		Files.createDirectories(libraryFolder);

		List<MavenArtifact> artifacts = getArtifacts(nashornVersion, asmVersion);
		List<Path> jars = new ArrayList<>();

		for (MavenArtifact artifact : artifacts) {
			Path jar = libraryFolder.resolve(nashornVersion).resolve(artifact.getFileName());

			if (Files.notExists(jar)) {
				if (!autoDownload) {
					logWarning("Missing optional Javascript dependency: " + artifact.getCoordinate());
					return List.of();
				}

				Files.createDirectories(jar.getParent());
				downloadArtifact(artifact, jar);
			}

			jars.add(jar);
		}

		return jars;
	}

	/**
	 * Gets the local Nashorn library folder.
	 *
	 * @return library folder
	 */
	private Path getLibraryFolder() {
		return plugin.getDataFolder().toPath().resolve("libs").resolve("nashorn");
	}

	/**
	 * Gets the artifact list for a Nashorn version.
	 *
	 * @param nashornVersion Nashorn version
	 * @param asmVersion ASM version
	 * @return artifact list
	 */
	private List<MavenArtifact> getArtifacts(String nashornVersion, String asmVersion) {
		List<MavenArtifact> artifacts = new ArrayList<>();

		artifacts.add(new MavenArtifact("org.openjdk.nashorn", "nashorn-core", nashornVersion));
		artifacts.add(new MavenArtifact("org.ow2.asm", "asm", asmVersion));
		artifacts.add(new MavenArtifact("org.ow2.asm", "asm-commons", asmVersion));
		artifacts.add(new MavenArtifact("org.ow2.asm", "asm-tree", asmVersion));
		artifacts.add(new MavenArtifact("org.ow2.asm", "asm-util", asmVersion));

		return artifacts;
	}

	/**
	 * Downloads an artifact from Maven Central.
	 *
	 * @param artifact artifact to download
	 * @param target target file
	 * @throws IOException if the artifact cannot be downloaded
	 */
	private void downloadArtifact(MavenArtifact artifact, Path target) throws IOException {
		Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");

		logInfo("Downloading optional Javascript dependency: " + artifact.getCoordinate());

		HttpURLConnection connection = (HttpURLConnection) URI.create(artifact.getMavenCentralJarUrl()).toURL()
				.openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("User-Agent", "AdvancedCore-JavascriptEngine");

		int responseCode = connection.getResponseCode();

		if (responseCode < 200 || responseCode >= 300) {
			connection.disconnect();
			throw new IOException("Failed to download " + artifact.getCoordinate() + ", HTTP " + responseCode);
		}

		try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(tempFile)) {
			input.transferTo(output);
		} finally {
			connection.disconnect();
		}

		if (Files.size(tempFile) <= 0) {
			Files.deleteIfExists(tempFile);
			throw new IOException("Downloaded empty jar for " + artifact.getCoordinate());
		}

		Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

		logInfo("Downloaded optional Javascript dependency: " + target.getFileName());
	}

	/**
	 * Creates a URL class loader for dependency jars.
	 *
	 * @param jars jar paths
	 * @return class loader
	 * @throws IOException if a jar URL cannot be created
	 */
	private URLClassLoader createClassLoader(List<Path> jars) throws IOException {
		URL[] urls = new URL[jars.size()];

		for (int i = 0; i < jars.size(); i++) {
			urls[i] = jars.get(i).toUri().toURL();
		}

		return new URLClassLoader(urls, getClass().getClassLoader());
	}

	/**
	 * Gets the no-argument getScriptEngine method from the Nashorn factory.
	 *
	 * @param foundFactory Nashorn factory class
	 * @return method, or null if not found
	 */
	private Method getScriptEngineMethod(Class<?> foundFactory) {
		for (Method method : foundFactory.getDeclaredMethods()) {
			if (method.getParameterCount() == 0 && method.getName().equals("getScriptEngine")) {
				method.setAccessible(true);
				return method;
			}
		}

		return null;
	}

	/**
	 * Closes the current downloaded class loader.
	 */
	public void closeExistingClassLoader() {
		if (nashornClassLoader != null) {
			closeQuietly(nashornClassLoader);
			nashornClassLoader = null;
		}
	}

	/**
	 * Closes a URL class loader without throwing an exception.
	 *
	 * @param loader loader to close
	 */
	private void closeQuietly(URLClassLoader loader) {
		try {
			loader.close();
		} catch (IOException ignored) {
		}
	}

	/**
	 * Logs an info message.
	 *
	 * @param message message to log
	 */
	private void logInfo(String message) {
		if (plugin != null) {
			plugin.getLogger().info(message);
		}
	}

	/**
	 * Logs a warning message.
	 *
	 * @param message message to log
	 */
	private void logWarning(String message) {
		if (plugin != null) {
			plugin.getLogger().warning(message);
		}
	}

	/**
	 * Represents a Maven artifact.
	 */
	private static class MavenArtifact {

		@Getter
		@Setter
		private String groupId;

		@Getter
		@Setter
		private String artifactId;

		@Getter
		@Setter
		private String version;

		/**
		 * Creates a Maven artifact.
		 *
		 * @param groupId group id
		 * @param artifactId artifact id
		 * @param version artifact version
		 */
		MavenArtifact(String groupId, String artifactId, String version) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
		}

		/**
		 * Gets the Maven coordinate.
		 *
		 * @return Maven coordinate
		 */
		String getCoordinate() {
			return groupId + ":" + artifactId + ":" + version;
		}

		/**
		 * Gets the jar file name.
		 *
		 * @return jar file name
		 */
		String getFileName() {
			return artifactId + "-" + version + ".jar";
		}

		/**
		 * Gets the Maven Central jar URL.
		 *
		 * @return Maven Central jar URL
		 */
		String getMavenCentralJarUrl() {
			return "https://repo1.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
					+ "/" + getFileName();
		}
	}
}