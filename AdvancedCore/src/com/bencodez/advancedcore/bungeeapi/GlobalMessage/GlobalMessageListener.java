package com.bencodez.advancedcore.bungeeapi.GlobalMessage;

import java.util.ArrayList;

import lombok.Getter;

public abstract class GlobalMessageListener {
	@Getter
	private String subChannel;

	public GlobalMessageListener(String subChannel) {
		this.subChannel = subChannel;
	}

	public void sendMessage(GlobalMessageHandler globalMessageHandler, String subChannel, String... messageData) {
		globalMessageHandler.sendMessage(subChannel, messageData);
	}

	public abstract void onReceive(ArrayList<String> message);
}
