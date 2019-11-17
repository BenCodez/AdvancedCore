package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import lombok.Getter;

public class SocketHandler {
	@Getter
	private SocketServer server;
	@Getter
	private ArrayList<SocketReceiver> receiving;

	public SocketHandler(String threadName, String host, int port, File encryptionFile) {
		receiving = new ArrayList<SocketReceiver>();

		server = new SocketServer(threadName, host, port, encryptionFile) {

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

	public void add(SocketReceiver receive) {
		receiving.add(receive);
	}

	public void closeConnection() {
		server.close();
	}
}
