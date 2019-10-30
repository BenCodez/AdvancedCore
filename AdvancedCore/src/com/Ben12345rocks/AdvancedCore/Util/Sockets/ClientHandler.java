package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
	private Socket clientSocket;
	private String host;
	private int port;

	public ClientHandler(String host, int port) {
		this.host = host;
		this.port = port;
	}

	private void connect() {
		try {
			if (clientSocket != null) {
				clientSocket.close();
			}
			clientSocket = new Socket(host, port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(String... msgs) {
		connect();
		String msg = msgs[0];
		for (int i = 1; i < msgs.length; i++) {
			msg += "%||%";
			msg += msgs[i];
		}
		try {
			DataOutputStream ds = new DataOutputStream(clientSocket.getOutputStream());
			ds.writeUTF(msg);
			System.out.println("out: " + msgs);
			ds.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public void stopConnection() {
		try {
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
