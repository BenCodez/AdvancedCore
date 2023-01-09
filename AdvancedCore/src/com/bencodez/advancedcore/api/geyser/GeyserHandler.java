package com.bencodez.advancedcore.api.geyser;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

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

	public UUID getFloodgateUUID(String name) {
		try {
			return FloodgateApi.getInstance().getUuidFor(name).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String getFloodgateName(UUID fromString) {
		return FloodgateApi.getInstance().getPlayer(fromString).getCorrectUsername();
	}

	public FloodgatePlayer getFloodgatePlayer(UUID uuid) {
		return FloodgateApi.getInstance().getPlayer(uuid);
	}

}