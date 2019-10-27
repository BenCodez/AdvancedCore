package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TimerTask;

import lombok.Getter;

public abstract class SocketServer extends Thread {
	@Getter
	private String host;
	@Getter
	private int port;

	private boolean running = true;

	private ServerSocket server;
	private String version;

	public abstract void onReceive(String[] data);

	public SocketServer(String version, String host, int port) {
		super(version);
		this.version = version;
		this.host = host;
		this.port = port;
		try {
			server = new ServerSocket();
			server.bind(new InetSocketAddress(host, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (running) {
			try (Socket socket = server.accept()) {
				socket.setSoTimeout(5000); // Don't hang on slow connections.
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

				// Send them plugin version
				writer.write(version);
				writer.newLine();
				writer.flush();

				final String msg = dis.readUTF();
				new TimerTask() {

					@Override
					public void run() {
						onReceive(msg.split("%||%"));
					}
				};

				dis.close();
				writer.close();
				socket.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void close() {
		try {
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
