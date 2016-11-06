package com.Ben12345rocks.AdvancedCore.Exceptions;

public class FileDirectoryException extends Exception {
	private static final long serialVersionUID = 3691439344307857655L;

	public FileDirectoryException() {

	}

	public FileDirectoryException(String message) {
		super(message);
	}

	public FileDirectoryException(Throwable cause) {
		super(cause);
	}

	public FileDirectoryException(String message, Throwable cause) {
		super(message, cause);
	}
}
