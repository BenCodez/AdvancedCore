package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.util.ArrayList;

import lombok.Getter;

public class SocketHandler {
	@Getter
	private SocketServer server;
	@Getter
	private ArrayList<SocketReceiver> receiving;

	public SocketHandler(String threadName, String host, int port) {
		server = new SocketServer(threadName, host, port) {
			
			@Override
			public void onReceive(String[] data) {
				for (SocketReceiver r : receiving) {
					r.onReceive(data);
				}
			}
		};
		receiving = new ArrayList<SocketReceiver>();
	}
	
	public void closeConnection() {
		server.close();
	}
}
