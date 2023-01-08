package com.bencodez.advancedcore.api.geyser;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

public class GeyserHandler {
	public GeyserHandler() {

	}

	public boolean isFloodgatePlayer(UUID uuid) {
		return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
	}

	public boolean isFloodgatePlayer(String name) {
		for (FloodgatePlayer p : FloodgateApi.getInstance().getPlayers()) {
			if (p.getCorrectUsername().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public CompletableFuture<UUID> getFloodgateUUID(String name) {
		return FloodgateApi.getInstance().getUuidFor(name);
	}

	public String getFloodgateName(UUID fromString) {
		return FloodgateApi.getInstance().getPlayer(fromString).getCorrectUsername();
	}

	public FloodgatePlayer getFloodgatePlayer(UUID uuid) {
		return FloodgateApi.getInstance().getPlayer(uuid);
	}

}