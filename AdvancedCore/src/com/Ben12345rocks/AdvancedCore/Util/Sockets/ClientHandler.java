package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import com.Ben12345rocks.AdvancedCore.Util.Encryption.EncryptionHandler;

public class ClientHandler {
	private Socket clientSocket;
	private String host;
	private int port;
	private EncryptionHandler encryptionHandler;
	private boolean debug = false;

	public ClientHandler(String host, int port, EncryptionHandler handle) {
		this.host = host;
		this.port = port;
		encryptionHandler = handle;
	}
	
	public ClientHandler(String host, int port, EncryptionHandler handle,boolean debug) {
		this.host = host;
		this.port = port;
		encryptionHandler = handle;
		this.debug = debug;
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
		sendMessage(debug, msgs);
	}

	public void sendMessage(boolean debug, String... msgs) {
		connect();
		String msg = msgs[0];
		for (int i = 1; i < msgs.length; i++) {
			msg += "%line%";
			msg += msgs[i];
		}
		String encrypted = encryptionHandler.encrypt(msg);
		try {
			DataOutputStream ds = new DataOutputStream(clientSocket.getOutputStream());
			ds.writeUTF(encrypted);
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
