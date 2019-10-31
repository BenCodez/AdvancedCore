package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import lombok.Getter;

public class SocketHandler {
	@Getter
	private SocketServer server;
	@Getter
	private ArrayList<SocketReceiver> receiving;

	public SocketHandler(String threadName, String host, int port) {
		receiving = new ArrayList<SocketReceiver>();

		server = new SocketServer(threadName, host, port) {

			@Override
			public void onReceive(String[] data) {
				for (SocketReceiver r : receiving) {
					TimerTask task = new TimerTask() {

						@Override
						public void run() {
							r.onReceive(data);
						}
					};
					if (r.getSocketDelay() > 0) {
						new Timer().schedule(task, r.getSocketDelay());
					} else {
						new Timer().schedule(task, 0);
					}

				}
			}
		};

	}

	public void closeConnection() {
		server.close();
	}

	public void add(SocketReceiver receive) {
		receiving.add(receive);
	}
}
