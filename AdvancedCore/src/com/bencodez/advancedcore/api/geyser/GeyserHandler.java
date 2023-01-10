package com.bencodez.advancedcore.api.geyser;

import java.util.UUID;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

public class GeyserHandler {
	public GeyserHandler() {

	}

	public boolean isFloodgatePlayer(UUID uuid) {
		return GeyserApi.api().isBedrockPlayer(uuid);
	}

	public boolean isFloodgatePlayer(String name) {
		for (GeyserConnection p : GeyserApi.api().onlineConnections()) {
			if (p.javaUsername().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public UUID getFloodgateUUID(String name) {
		for (GeyserConnection p : GeyserApi.api().onlineConnections()) {
			if (p.javaUsername().equals(name)) {
				return p.javaUuid();
			}
		}
		return null;
	}

	public String getFloodgateName(UUID uuid) {
		return GeyserApi.api().connectionByUuid(uuid).javaUsername();
	}

	public GeyserConnection getFloodgatePlayer(UUID uuid) {
		return GeyserApi.api().connectionByUuid(uuid);
	}

}