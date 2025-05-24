package com.bencodez.advancedcore.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.data.ServerData;

public class BaseTest {
	private static BaseTest instance;
	private MockedStatic<AdvancedCorePlugin> advancedCorePluginMockedStatic;

	public AdvancedCorePlugin plugin;
	public AdvancedCoreConfigOptions options;
	public ServerData serverDataFile;
	public Logger logger;

	private BaseTest() {
		plugin = mock(AdvancedCorePlugin.class);
		options = mock(AdvancedCoreConfigOptions.class);
		serverDataFile = mock(ServerData.class);
		logger = mock(Logger.class);

		advancedCorePluginMockedStatic = mockStatic(AdvancedCorePlugin.class);
		advancedCorePluginMockedStatic.when(AdvancedCorePlugin::getInstance).thenReturn(plugin);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);
		when(plugin.getLogger()).thenReturn(logger);
	}

	public static BaseTest getInstance() {
		if (instance == null) {
			instance = new BaseTest();
		}
		return instance;
	}

	@AfterAll
	public void close() {
		if (advancedCorePluginMockedStatic != null) {
			advancedCorePluginMockedStatic.close();
		}
	}
}