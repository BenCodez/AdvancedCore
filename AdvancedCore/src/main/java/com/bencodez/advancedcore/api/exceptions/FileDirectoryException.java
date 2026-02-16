package com.bencodez.advancedcore.api.exceptions;

/**
 * Exception for file directory errors.
 */
public class FileDirectoryException extends Exception {
	private static final long serialVersionUID = 3691439344307857655L;

	/**
	 * Constructor for FileDirectoryException.
	 */
	public FileDirectoryException() {

	}

	/**
	 * Constructor for FileDirectoryException with message.
	 *
	 * @param message the error message
	 */
	public FileDirectoryException(String message) {
		super(message);
	}

	/**
	 * Constructor for FileDirectoryException with message and cause.
	 *
	 * @param message the error message
	 * @param cause the cause
	 */
	public FileDirectoryException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for FileDirectoryException with cause.
	 *
	 * @param cause the cause
	 */
	public FileDirectoryException(Throwable cause) {
		super(cause);
	}
}
