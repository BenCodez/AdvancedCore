package com.Ben12345rocks.AdvancedCore.Util.Sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler {
	private Socket clientSocket;
	private PrintWriter out;
	private BufferedReader in;
	private String host;
	private int port;
	private boolean connected = false;

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
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			connected = true;
		} catch (IOException e) {
			e.printStackTrace();
			connected = false;
		}
	}

	public String sendMessage(String... msgs) {
		if (!connected || !clientSocket.isBound()) {
			connect();
		}
		if (msgs.length == 0) {
			return "";
		}
		String msg = msgs[0];
		for (int i = 1; i < msgs.length; i++) {
			msg += "%||%";
			msg += msgs[i];
		}
		out.println(msg);
		System.out.println(msg);
		String resp = null;
		try {
			resp = in.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return resp;
	}

	public void stopConnection() {
		try {
			in.close();
			out.close();
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
