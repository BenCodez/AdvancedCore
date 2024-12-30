package com.bencodez.advancedcore.bungeeapi.sockets;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;

import lombok.Getter;

public abstract class SocketHandler {
	@Getter
	private ArrayList<SocketReceiver> receiving;
	@Getter
	private SocketServer server;

	private ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

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
		server = null;
	}

	public abstract void log(String str);

	public void start(String threadName, String host, int port, EncryptionHandler handle, boolean debug) {
		receiving = new ArrayList<>();

		server = new SocketServer(threadName, host, port, handle, debug) {

			@Override
			public void logger(String str) {
				log(str);
			}

			@Override
			public void onReceive(String[] data) {
				if (data.length > 0) {
					for (SocketReceiver r : receiving) {
						if (r.getSocketDelay() > 0) {
							timer.schedule(new Runnable() {

								@Override
								public void run() {
									r.onReceive(data[0], data);
								}
							}, r.getSocketDelay(), TimeUnit.MILLISECONDS);
						} else {
							timer.submit(new Runnable() {

								@Override
								public void run() {
									r.onReceive(data[0], data);
								}
							});
						}

					}
				} else {
					log("Socket data invalid");
				}
			}
		};

		log("Loading socket server: " + server.getHost() + ":" + server.getPort());
	}
}
