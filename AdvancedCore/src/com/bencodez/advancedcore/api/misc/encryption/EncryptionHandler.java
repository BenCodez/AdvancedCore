package com.bencodez.advancedcore.api.misc.encryption;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionHandler {
    private static Cipher dcipher;
    private static Cipher ecipher;

    private static SecretKey key;

    public EncryptionHandler(File file) {
        try {
            if (file.exists()) {
                loadKey(file);
            } else {
                generateKey();
                save(file);
            }

            ecipher = Cipher.getInstance("AES");
            dcipher = Cipher.getInstance("AES");

            // initialize the ciphers with the given key
            ecipher.init(Cipher.ENCRYPT_MODE, key);
            dcipher.init(Cipher.DECRYPT_MODE, key);

            String msg = "This is a classified message!";
            String encrypted = encrypt(msg);
            String decrypted = decrypt(encrypted);
            if (!msg.equals(decrypted)) {
                System.out.println("VotingPlugin: Encryption/Decryption failed: " + decrypted);
            }
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No Such Algorithm:" + e.getMessage());
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            System.out.println("No Such Padding:" + e.getMessage());
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            System.out.println("Invalid Key:" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String decrypt(String str) {
        try {
            // decode with base64 to get bytes
            byte[] dec = Base64.getDecoder().decode(str.getBytes());
            byte[] utf8 = dcipher.doFinal(dec);
            // create new string based on the specified charset
            return new String(utf8, "UTF8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String encrypt(String str) {
        try {
            // encode the string into a sequence of bytes using the named charset
            // storing the result into a new byte array.
            byte[] utf8 = str.getBytes("UTF8");
            byte[] enc = ecipher.doFinal(utf8);
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            e.printStackTrace();
		}
		return null;
    }

    private void generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256); // Use 256-bit AES
        key = keyGenerator.generateKey();
    }

    private void loadKey(File file) throws IOException {
        String str = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        key = new SecretKeySpec(Base64.getDecoder().decode(str), "AES");
    }

    private void save(File file) throws IOException {
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(Base64.getEncoder().withoutPadding().encodeToString(key.getEncoded()));
        fileWriter.close();
    }
}
