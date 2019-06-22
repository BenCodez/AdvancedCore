package com.Ben12345rocks.AdvancedCore.Util.Misc;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class NameFetcher implements Callable<Map<UUID, String>> {
	private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private final JsonParser jsonParser = new JsonParser();
	private final List<UUID> uuids;

	public NameFetcher(List<UUID> uuids) {
		this.uuids = ImmutableList.copyOf(uuids);
	}

	@Override
	public Map<UUID, String> call() throws Exception {
		Map<UUID, String> uuidStringMap = new HashMap<UUID, String>();
		for (UUID uuid : uuids) {
			HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL + uuid.toString().replace("-", ""))
					.openConnection();
			JsonObject response = (JsonObject) jsonParser.parse(new InputStreamReader(connection.getInputStream()));
			String name = (String) response.get("name").getAsString();
			if (name == null) {
				continue;
			}
			String cause = (String) response.get("cause").getAsString();
			String errorMessage = (String) response.get("errorMessage").getAsString();
			if (errorMessage != null && errorMessage.equals("TooManyRequestsException")) {
				AdvancedCorePlugin.getInstance().debug("Sent too many requests");
			}
			if (cause != null && cause.length() > 0) {
				throw new IllegalStateException(errorMessage);
			}
			uuidStringMap.put(uuid, name);
		}
		return uuidStringMap;
	}
}