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
	private String version;

	public ClientHandler(String version, String host, int port) {
		this.version = version;
		try {
			clientSocket = new Socket(host, port);
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean sendMessage(String msg) {
		out.println(msg);
		String resp;
		try {
			resp = in.readLine();
			if (resp.equals(version)) {
				return true;
			} else {
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
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
