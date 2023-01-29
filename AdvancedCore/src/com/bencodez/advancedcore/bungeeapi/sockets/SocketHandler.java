package com.bencodez.advancedcore.bungeeapi.sockets;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;

import lombok.Getter;

public abstract class SocketHandler {
	@Getter
	private ArrayList<SocketReceiver> receiving;
	@Getter
	private SocketServer server;

	public SocketHandler(String threadName, String host, int port, EncryptionHandler handle) {
		start(threadName, host, port, handle, false);
	}

	public SocketHandler(String threadName, String host, int port, EncryptionHandler handle, boolean debug) {
		start(threadName, host, port, handle, debug);
	}

	public void add(SocketReceiver receive) {
		receiving.add(receive);
	}

	public void closeConnection() {
		server.close();
	}

	public abstract void log(String str);

	public void start(String threadName, String host, int port, EncryptionHandler handle, boolean debug) {
		receiving = new ArrayList<SocketReceiver>();

		server = new SocketServer(threadName, host, port, handle, debug) {

			@Override
			public void onReceive(String[] data) {
				if (data.length > 0) {
					for (SocketReceiver r : receiving) {
						TimerTask task = new TimerTask() {

							@Override
							public void run() {
								r.onReceive(data[0], data);
							}
						};
						if (r.getSocketDelay() > 0) {
							new Timer().schedule(task, r.getSocketDelay());
						} else {
							new Timer().schedule(task, 0);
						}

					}
				} else {
					System.out.print("Socket data invalid");
				}
			}

			@Override
			public void logger(String str) {
				log(str);
			}
		};

		log("Loading socket server: " + server.getHost() + ":" + server.getPort());
	}
}
