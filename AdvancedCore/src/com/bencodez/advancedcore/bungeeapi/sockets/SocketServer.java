package com.bencodez.advancedcore.bungeeapi.sockets;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;

import lombok.Getter;

public abstract class SocketServer extends Thread {

	@Getter
	private boolean debug = false;

	private EncryptionHandler encryptionHandler;

	@Getter
	private String host;

	@Getter
	private int port;

	private boolean running = true;

	private ServerSocket server;

	public SocketServer(String version, String host, int port, EncryptionHandler handle, boolean debug) {
		super(version);
		this.host = host;
		this.port = port;
		encryptionHandler = handle;
		this.debug = debug;
		try {
			server = new ServerSocket();
			server.bind(new InetSocketAddress(host, port));
			start();
		} catch (IOException e) {
			System.out.println("Failed to bind to " + host + ":" + port);
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			running = false;
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public abstract void logger(String str);

	public abstract void onReceive(String[] data);

	@Override
	public void run() {
		while (running) {
			try {
				Socket socket = server.accept();
				socket.setSoTimeout(5000); // Don't hang on slow connections.
				DataInputStream dis = new DataInputStream(socket.getInputStream());

				final String msg = encryptionHandler.decrypt(dis.readUTF());
				if (debug) {
					logger("Debug: Socket Receiving: " + msg);
				}
				onReceive(msg.split("%line%"));
				dis.close();
				socket.close();
			} catch (EOFException e) {
				logger("Error occured while receiving socket message, enable debug to see more: " + e.getMessage());
				if (debug) {
					e.printStackTrace();
				}
			} catch (Exception ex) {
				logger("Error occured while receiving socket message");
				ex.printStackTrace();
			}
		}

	}
}
