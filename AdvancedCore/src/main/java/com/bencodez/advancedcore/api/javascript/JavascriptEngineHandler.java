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

import com.bencodez.advancedcore.AdvancedCorePlugin;

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
	private AdvancedCorePlugin plugin;

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

	@Getter
	@Setter
	private ScriptEngine cachedEngine;

	@Getter
	@Setter
	private boolean prepared;

	@Getter
	@Setter
	private boolean preparing;

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
	public void init(AdvancedCorePlugin plugin, boolean enabled, boolean autoDownload) {
		this.plugin = plugin;
		this.enabled = enabled;
		this.autoDownload = autoDownload;
		this.prepared = false;
		this.preparing = false;
		this.cachedEngine = null;
		this.factory = null;
		this.methodToUse = null;
		this.loadedVersion = null;

		logDebug("JavascriptEngine: initialized. Enabled=" + enabled + ", AutoDownload=" + autoDownload
				+ ", LibraryFolder=" + getLibraryFolderSafe());
	}

	/**
	 * Prepares the Javascript engine so dependencies are downloaded and the engine is ready before first use.
	 *
	 * @return true if an engine is ready, false otherwise
	 */
	public boolean prepareEngine() {
		logDebug("JavascriptEngine: prepare requested.");

		if (prepared && cachedEngine != null) {
			logEngine("JavascriptEngine: already prepared", cachedEngine);
			return true;
		}

		if (preparing) {
			logDebug("JavascriptEngine: prepare already in progress.");
			return cachedEngine != null;
		}

		preparing = true;

		try {
			cachedEngine = loadJSScriptEngine();

			if (cachedEngine == null) {
				prepared = false;
				logWarning("JavascriptEngine: prepare failed, no JavaScript engine is available.");
				return false;
			}

			prepared = true;
			logEngine("JavascriptEngine: prepared engine", cachedEngine);
			return true;
		} finally {
			preparing = false;
		}
	}

	/**
	 * Gets a Javascript script engine.
	 *
	 * @return the script engine
	 */
	public ScriptEngine getJSScriptEngine() {
		logDebug("JavascriptEngine: requested JavaScript engine.");

		if (cachedEngine != null) {
			logEngine("JavascriptEngine: returning cached engine", cachedEngine);
			return cachedEngine;
		}

		logDebug("JavascriptEngine: engine was not prepared yet, preparing now.");
		prepareEngine();

		if (cachedEngine != null) {
			return cachedEngine;
		}

		return null;
	}

	/**
	 * Loads a Javascript script engine.
	 *
	 * @return the script engine
	 */
	private ScriptEngine loadJSScriptEngine() {
		if (!enabled) {
			logDebug("JavascriptEngine: disabled by config.");
			return null;
		}

		ScriptEngine engine = getServerProvidedEngine();

		if (engine != null) {
			logEngine("JavascriptEngine: using server-provided engine", engine);
			return engine;
		}

		logDebug("JavascriptEngine: no server-provided JavaScript engine found.");
		logDebug("JavascriptEngine: trying downloaded Nashorn " + PRIMARY_NASHORN_VERSION + " with ASM "
				+ ASM_VERSION_FOR_PRIMARY + ".");

		engine = getDownloadedEngine(PRIMARY_NASHORN_VERSION, ASM_VERSION_FOR_PRIMARY);

		if (engine != null) {
			logEngine("JavascriptEngine: using downloaded Nashorn " + PRIMARY_NASHORN_VERSION, engine);
			return engine;
		}

		logWarning("JavascriptEngine: failed to load Nashorn " + PRIMARY_NASHORN_VERSION + ", trying fallback "
				+ FALLBACK_NASHORN_VERSION + " with ASM " + ASM_VERSION_FOR_FALLBACK + ".");

		engine = getDownloadedEngine(FALLBACK_NASHORN_VERSION, ASM_VERSION_FOR_FALLBACK);

		if (engine == null) {
			logWarning("JavascriptEngine: no JavaScript engine is available. JavaScript features will be disabled.");
			return null;
		}

		logEngine("JavascriptEngine: using downloaded Nashorn fallback " + FALLBACK_NASHORN_VERSION, engine);
		return engine;
	}

	/**
	 * Gets a Javascript engine already available to the JVM or server classpath.
	 *
	 * @return the script engine, or null if none exists
	 */
	private ScriptEngine getServerProvidedEngine() {
		logDebug("JavascriptEngine: checking ScriptEngineManager name 'js'.");
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");

		if (engine != null) {
			logEngine("JavascriptEngine: found engine by name 'js'", engine);
			return engine;
		}

		logDebug("JavascriptEngine: checking ScriptEngineManager name 'JavaScript'.");
		engine = new ScriptEngineManager().getEngineByName("JavaScript");

		if (engine != null) {
			logEngine("JavascriptEngine: found engine by name 'JavaScript'", engine);
			return engine;
		}

		logDebug("JavascriptEngine: checking ScriptEngineManager name 'nashorn'.");
		engine = new ScriptEngineManager().getEngineByName("nashorn");

		if (engine != null) {
			logEngine("JavascriptEngine: found engine by name 'nashorn'", engine);
			return engine;
		}

		logDebug("JavascriptEngine: checking direct Nashorn factory class " + NASHORN_FACTORY_CLASS + ".");

		try {
			Class<?> foundFactory = Class.forName(NASHORN_FACTORY_CLASS);
			Method foundMethod = getScriptEngineMethod(foundFactory);

			if (foundMethod == null) {
				logWarning("JavascriptEngine: server-provided Nashorn factory found, but getScriptEngine was not found.");
				return null;
			}

			engine = (ScriptEngine) foundMethod.invoke(foundFactory.getDeclaredConstructor().newInstance());

			if (engine != null) {
				factory = foundFactory;
				methodToUse = foundMethod;
				logEngine("JavascriptEngine: created engine from server-provided Nashorn factory", engine);
			} else {
				logWarning("JavascriptEngine: server-provided Nashorn factory returned null engine.");
			}

			return engine;
		} catch (ClassNotFoundException ignored) {
			logDebug("JavascriptEngine: server-provided Nashorn factory class was not found.");
			return null;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| InstantiationException | NoSuchMethodException e) {
			logWarning("JavascriptEngine: failed to create server-provided Nashorn engine: "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
			logDebug(e);
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
			logWarning("JavascriptEngine: handler has not been initialized with a plugin instance.");
			return null;
		}

		logDebug("JavascriptEngine: preparing downloaded Nashorn " + nashornVersion + ".");
		logDebug("JavascriptEngine: dependency folder: " + getLibraryFolder().resolve(nashornVersion));

		try {
			List<Path> jars = getOrDownloadJars(nashornVersion, asmVersion);

			if (jars.isEmpty()) {
				logWarning("JavascriptEngine: no jars available for Nashorn " + nashornVersion + ".");
				return null;
			}

			logDebug("JavascriptEngine: creating classloader for Nashorn " + nashornVersion + " with " + jars.size()
					+ " jar(s).");

			for (Path jar : jars) {
				logDebug("JavascriptEngine: classloader jar: " + jar);
			}

			URLClassLoader loader = createClassLoader(jars);

			logDebug("JavascriptEngine: loading Nashorn factory class from downloaded classloader.");
			Class<?> foundFactory = Class.forName(NASHORN_FACTORY_CLASS, true, loader);
			Method foundMethod = getScriptEngineMethod(foundFactory);

			if (foundMethod == null) {
				closeQuietly(loader);
				logWarning("JavascriptEngine: Nashorn " + nashornVersion
						+ " was loaded, but no getScriptEngine method was found.");
				return null;
			}

			logDebug("JavascriptEngine: invoking Nashorn " + nashornVersion + " factory getScriptEngine.");
			ScriptEngine engine = (ScriptEngine) foundMethod.invoke(foundFactory.getDeclaredConstructor().newInstance());

			if (engine == null) {
				closeQuietly(loader);
				logWarning("JavascriptEngine: Nashorn " + nashornVersion + " returned a null script engine.");
				return null;
			}

			closeExistingClassLoader();

			nashornClassLoader = loader;
			factory = foundFactory;
			methodToUse = foundMethod;
			loadedVersion = nashornVersion;

			logDebug("JavascriptEngine: loaded optional Nashorn JavaScript engine " + nashornVersion + ".");
			logEngine("JavascriptEngine: downloaded engine details", engine);
			return engine;
		} catch (Exception e) {
			logWarning("JavascriptEngine: failed to load Nashorn " + nashornVersion + ": "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
			logDebug(e);
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

		logDebug("JavascriptEngine: checking dependencies for Nashorn " + nashornVersion + " in "
				+ libraryFolder.resolve(nashornVersion) + ".");

		List<MavenArtifact> artifacts = getArtifacts(nashornVersion, asmVersion);
		List<Path> jars = new ArrayList<>();

		for (MavenArtifact artifact : artifacts) {
			Path jar = libraryFolder.resolve(nashornVersion).resolve(artifact.getFileName());

			if (Files.exists(jar)) {
				logDebug("JavascriptEngine: found dependency: " + artifact.getCoordinate() + " at " + jar);
				jars.add(jar);
				continue;
			}

			logWarning("JavascriptEngine: missing dependency: " + artifact.getCoordinate() + " expected at " + jar);

			if (!autoDownload) {
				logWarning("JavascriptEngine: auto-download is disabled, cannot download "
						+ artifact.getCoordinate() + ".");
				return List.of();
			}

			Files.createDirectories(jar.getParent());
			downloadArtifact(artifact, jar);
			jars.add(jar);
		}

		logDebug("JavascriptEngine: dependency check complete for Nashorn " + nashornVersion + ".");
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
	 * Gets the local Nashorn library folder safely.
	 *
	 * @return library folder string
	 */
	private String getLibraryFolderSafe() {
		if (plugin == null) {
			return "plugin-not-set";
		}

		return getLibraryFolder().toString();
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
		artifacts.add(new MavenArtifact("org.ow2.asm", "asm-analysis", asmVersion));
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

		logDebug("JavascriptEngine: downloading optional dependency: " + artifact.getCoordinate());
		logDebug("JavascriptEngine: download URL: " + artifact.getMavenCentralJarUrl());
		logDebug("JavascriptEngine: download target: " + target);

		HttpURLConnection connection = (HttpURLConnection) URI.create(artifact.getMavenCentralJarUrl()).toURL()
				.openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("User-Agent", "AdvancedCore-JavascriptEngine");

		int responseCode = connection.getResponseCode();
		logDebug("JavascriptEngine: Maven Central response for " + artifact.getCoordinate() + ": HTTP "
				+ responseCode);

		if (responseCode < 200 || responseCode >= 300) {
			connection.disconnect();
			throw new IOException("Failed to download " + artifact.getCoordinate() + ", HTTP " + responseCode);
		}

		try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(tempFile)) {
			input.transferTo(output);
		} finally {
			connection.disconnect();
		}

		long size = Files.size(tempFile);
		logDebug("JavascriptEngine: downloaded " + artifact.getCoordinate() + " size=" + size + " bytes.");

		if (size <= 0) {
			Files.deleteIfExists(tempFile);
			throw new IOException("Downloaded empty jar for " + artifact.getCoordinate());
		}

		Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

		logDebug("JavascriptEngine: saved optional dependency: " + target);
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
			logDebug("JavascriptEngine: adding classloader URL: " + urls[i]);
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
			logDebug("JavascriptEngine: closing existing Nashorn classloader for version " + loadedVersion + ".");
			closeQuietly(nashornClassLoader);
			nashornClassLoader = null;
			loadedVersion = null;
		}
	}

	/**
	 * Clears the cached JavaScript engine.
	 */
	public void clearCachedEngine() {
		logDebug("JavascriptEngine: clearing cached engine.");
		cachedEngine = null;
		prepared = false;
		preparing = false;
		factory = null;
		methodToUse = null;
		closeExistingClassLoader();
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
	 * Logs script engine details.
	 *
	 * @param prefix message prefix
	 * @param engine script engine
	 */
	private void logEngine(String prefix, ScriptEngine engine) {
		if (engine == null) {
			logWarning(prefix + ": null");
			return;
		}

		try {
			logDebug(prefix + ": engineName=" + engine.getFactory().getEngineName()
					+ ", engineVersion=" + engine.getFactory().getEngineVersion()
					+ ", languageName=" + engine.getFactory().getLanguageName()
					+ ", languageVersion=" + engine.getFactory().getLanguageVersion()
					+ ", names=" + engine.getFactory().getNames());
		} catch (Exception e) {
			logWarning(prefix + ": failed to read engine details: " + e.getMessage());
			logDebug(e);
		}
	}

	/**
	 * Logs a debug message.
	 *
	 * @param message message to log
	 */
	private void logDebug(String message) {
		if (plugin != null) {
			plugin.debug(message);
		}
	}

	/**
	 * Logs an exception in debug mode.
	 *
	 * @param throwable throwable to log
	 */
	private void logDebug(Throwable throwable) {
		if (plugin != null) {
			plugin.debug(throwable);
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