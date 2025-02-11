package com.bencodez.advancedcore.api.exceptions;

public class FileDirectoryException extends Exception {
	private static final long serialVersionUID = 3691439344307857655L;

	public FileDirectoryException() {

	}

	public FileDirectoryException(String message) {
		super(message);
	}

	public FileDirectoryException(String message, Throwable cause) {
		super(message, cause);
	}

	public FileDirectoryException(Throwable cause) {
		super(cause);
	}
}
