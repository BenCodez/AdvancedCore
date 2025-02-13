package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.misc.encryption.EncryptionHandler;

public class EncryptionHandlerTest {

	private File tempKeyFile;
	private EncryptionHandler encryptionHandler;

	@BeforeEach
	public void setUp() throws IOException {
		// Create a temporary file for key storage
		tempKeyFile = new File(System.getProperty("java.io.tmpdir"), "encryption_test_key");

		// Initialize EncryptionHandler
		encryptionHandler = new EncryptionHandler(tempKeyFile);
	}

	@AfterEach
	public void tearDown() {
		// Clean up the temporary file
		if (tempKeyFile.exists()) {
			tempKeyFile.delete();
		}
	}

	@Test
	public void testEncryptionDecryption() {
		String originalMessage = "Test encryption and decryption";

		String encryptedMessage = encryptionHandler.encrypt(originalMessage);
		assertNotNull(encryptedMessage, "Encrypted message should not be null");

		String decryptedMessage = encryptionHandler.decrypt(encryptedMessage);
		assertNotNull(decryptedMessage, "Decrypted message should not be null");

		assertEquals(originalMessage, decryptedMessage, "Decrypted message should be equal to the original");
	}

	@Test
	public void testKeyFileCreationAndLoading() throws IOException, NoSuchAlgorithmException {
		// Ensure the key file is created
		assertTrue(tempKeyFile.exists(), "Key file should be created on initialization");

		// Create a new handler to check if it loads the existing key
		EncryptionHandler newHandler = new EncryptionHandler(tempKeyFile);

		String originalMessage = "Another encryption test";
		String encryptedMessage = encryptionHandler.encrypt(originalMessage);
		String decryptedMessage = newHandler.decrypt(encryptedMessage);

		assertEquals(originalMessage, decryptedMessage, "Decrypted message with loaded key should match original");
	}

	@Test
	public void testSaveAndLoadKey() throws IOException {
		// Save the existing key
		encryptionHandler.save(tempKeyFile);

		// Load the key and ensure encryption still works
		EncryptionHandler newHandler = new EncryptionHandler(tempKeyFile);

		String originalMessage = "Persistence test";
		String encryptedMessage = encryptionHandler.encrypt(originalMessage);
		String decryptedMessage = newHandler.decrypt(encryptedMessage);

		assertEquals(originalMessage, decryptedMessage,
				"Decrypted message with saved and loaded key should match original");
	}
}