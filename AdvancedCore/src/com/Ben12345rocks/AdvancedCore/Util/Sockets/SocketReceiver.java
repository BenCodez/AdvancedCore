package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import lombok.Getter;

public abstract class SocketReceiver {
	@Getter
	private int socketDelay = 0;

	public abstract void onReceive(String[] data);

	public SocketReceiver setSocketDelay(int delay) {
		this.socketDelay = delay;
		return this;
	}

}
