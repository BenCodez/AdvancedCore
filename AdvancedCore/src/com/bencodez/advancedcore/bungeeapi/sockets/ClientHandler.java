
package com.bencodez.advancedcore.bungeeapi.sockets;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;
import com.bencodez.simpleapi.array.ArrayUtils;

public class ClientHandler {
	private Socket clientSocket;
	private boolean debug = false;
	private EncryptionHandler encryptionHandler;
	private String host;
	private int port;

	public ClientHandler(String host, int port, EncryptionHandler handle) {
		this.host = host;
		this.port = port;
		encryptionHandler = handle;
	}

	public ClientHandler(String host, int port, EncryptionHandler handle, boolean debug) {
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(boolean debug, String... msgs) {
		if (debug) {
			System.out.println("Socket Sending: " + ArrayUtils.makeStringList(ArrayUtils.convert(msgs)));
		}
		connect();
		String msg = msgs[0];
		for (int i = 1; i < msgs.length; i++) {
			msg += "%line%";
			msg += msgs[i];
		}
		String encrypted = encryptionHandler.encrypt(msg);
		try (DataOutputStream ds = new DataOutputStream(clientSocket.getOutputStream())) {
			ds.writeUTF(encrypted);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		stopConnection();
	}

	public void sendMessage(String... msgs) {
		sendMessage(debug, msgs);
	}

	public void stopConnection() {
		try {
			if (clientSocket != null) {
				clientSocket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
