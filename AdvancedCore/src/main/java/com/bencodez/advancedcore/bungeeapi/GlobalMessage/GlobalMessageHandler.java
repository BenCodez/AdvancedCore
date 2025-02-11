package com.bencodez.advancedcore.bungeeapi.GlobalMessage;

import java.util.ArrayList;

public abstract class GlobalMessageHandler {

	private ArrayList<GlobalMessageListener> globalMessageListeners = new ArrayList<>();

	public GlobalMessageHandler() {
	}

	public void addListener(GlobalMessageListener globalMessageListener) {
		globalMessageListeners.add(globalMessageListener);
	}

	public void onMessage(String subChannel, ArrayList<String> message) {
		for (GlobalMessageListener listener : globalMessageListeners) {
			if (listener.getSubChannel().equalsIgnoreCase(subChannel)) {
				listener.onReceive(message);
			}
		}
	}

	public abstract void sendMessage(String subChannel, String... messageData);
}
