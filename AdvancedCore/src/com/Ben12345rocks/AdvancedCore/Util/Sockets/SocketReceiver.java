package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import lombok.Getter;

public abstract class SocketReceiver {
	public SocketReceiver() {
	}

	private String ident = "";

	public SocketReceiver(String ident) {
		this.ident = ident;
	}

	@Getter
	private int socketDelay = 0;

	public void onReceive(String ident, String[] data) {
		if (ident.isEmpty() || ident.equalsIgnoreCase(this.ident)) {
			onReceive(data);
		}
	}

	public abstract void onReceive(String[] data);

	public SocketReceiver setSocketDelay(int delay) {
		this.socketDelay = delay;
		return this;
	}

}
