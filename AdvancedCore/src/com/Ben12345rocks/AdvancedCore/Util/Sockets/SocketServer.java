package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.Getter;

public abstract class SocketServer extends Thread {

	@Getter
	private String host;

	@Getter
	private int port;

	private boolean running = true;

	private ServerSocket server;

	public abstract void onReceive(String[] data);

	public SocketServer(String version, String host, int port) {
		super(version);
		this.host = host;
		this.port = port;
		try {
			server = new ServerSocket();
			server.bind(new InetSocketAddress(host, port));
			start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (running) {
			try {
				Socket socket = server.accept();
				socket.setSoTimeout(5000); // Don't hang on slow connections.
				DataInputStream dis = new DataInputStream(socket.getInputStream());

				final String msg = dis.readUTF();
				onReceive(msg.split("%line%"));

				dis.close();
				socket.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

	public void close() {
		try {
			server.close();
			running = false;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
