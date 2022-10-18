package com.sk.bds.ticket.api.util;

import org.apache.commons.io.FileUtils;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AesCipher {
    private static final String ENCRYPT_ALGORITHM = "AES/GCM/NoPadding";

    private static final int TAG_LENGTH_BIT = 128; // must be one of {128, 120, 112, 104, 96}
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    public static byte[] getRandomNonce(int numBytes) {
        byte[] nonce = new byte[numBytes];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    // AES secret key
    public static SecretKey randomSecret(int keySize) throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(keySize, SecureRandom.getInstanceStrong());
        return keyGen.generateKey();
    }

    // Password derived AES 256 bits secret key
    public static SecretKey getAESKeyFromPassword(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        //SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // iterationCount = 65536
        // keyLength = 256
        KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        return secret;
    }

    // hex representation
    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    // print hex with block size split
    public static String hexWithBlockSize(byte[] bytes, int blockSize) {
        String hex = hex(bytes);
        // one hex = 2 chars
        blockSize = blockSize * 2;
        // better idea how to print this?
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < hex.length()) {
            result.add(hex.substring(index, Math.min(index + blockSize, hex.length())));
            index += blockSize;
        }

        return result.toString();
    }

    public static byte[] encryptBytes(byte[] pTextBytes, String password) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // 16 bytes salt
        byte[] salt = getRandomNonce(SALT_LENGTH_BYTE);
        // GCM recommended 12 bytes iv?
        byte[] iv = getRandomNonce(IV_LENGTH_BYTE);
        // secret key from password
        SecretKey aesKeyFromPassword = getAESKeyFromPassword(password.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGORITHM);
        // ASE-GCM needs GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        byte[] cipherText = cipher.doFinal(pTextBytes);
        // prefix IV and Salt to cipher text
        byte[] cipherTextWithIvSalt = ByteBuffer.allocate(iv.length + salt.length + cipherText.length)
                .put(iv)
                .put(salt)
                .put(cipherText)
                .array();
        return cipherTextWithIvSalt;
    }

    // return a base64 encoded AES encrypted text
    public static String encrypt(String pText, String password) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException {
        byte[] pTextBytes = pText.getBytes(UTF_8);
        byte[] cipherTextWithIvSalt = encryptBytes(pTextBytes, password);
        // string representation, base64, send this string to other for decryption.
        return Base64.getEncoder().encodeToString(cipherTextWithIvSalt);
    }

    // we need the same password, salt and iv to decrypt it
    public static byte[] decryptBytes(byte[] cTextBytes, String password) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        // get back the iv and salt from the cipher text
        ByteBuffer bb = ByteBuffer.wrap(cTextBytes);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        bb.get(iv);
        byte[] salt = new byte[SALT_LENGTH_BYTE];
        bb.get(salt);
        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);
        // get back the aes key from the same password and salt
        SecretKey aesKeyFromPassword = getAESKeyFromPassword(password.toCharArray(), salt);
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, aesKeyFromPassword, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        byte[] plainTextBytes = cipher.doFinal(cipherText);
        return plainTextBytes;
    }

    public static String decrypt(String cText, String password) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException {
        byte[] cTextBytes = Base64.getDecoder().decode(cText.getBytes(UTF_8));
        byte[] plainTextBytes = decryptBytes(cTextBytes, password);
        return new String(plainTextBytes, UTF_8);
    }

    public static void encryptFile(String fromFile, String toFile, String password) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException, URISyntaxException, IOException {
        // read a normal txt file
        byte[] fileContent = Files.readAllBytes(Paths.get(ClassLoader.getSystemResource(fromFile).toURI()));
        // encrypt with a password
        byte[] encryptedBytes = encryptBytes(fileContent, password);
        // save a file
        Path path = Paths.get(toFile);
        Files.write(path, encryptedBytes);
    }

    public static String encryptFile(File fromFile, String password) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException, URISyntaxException, IOException {
        // read a normal txt file
        byte[] fileContent = FileUtils.readFileToByteArray(fromFile);
        String configText = new String(fileContent);
        System.out.println("encryptFile configText(size:" + configText.length() + ") data:>>>>>" + configText + "<<<<<End Of Content");
        // encrypt with a password
        byte[] encryptedText = encryptBytes(fileContent, password);
        return Base64.getEncoder().encodeToString(encryptedText);
    }

    public static byte[] decryptFile(String fromEncryptedFile, String password) throws Exception {
        // read a file
        byte[] fileContent = Files.readAllBytes(Paths.get(fromEncryptedFile));
        return decryptBytes(fileContent, password);
    }

    public static void example() throws Exception {
        String OUTPUT_FORMAT = "%-30s:%s";
        String PASSWORD = "this is a password";
        String pText = "AES-GSM Password-Bases encryption!";
        String encryptedTextBase64 = encrypt(pText, PASSWORD);
        System.out.println("\n------ AES GCM Password-based Encryption ------");
        System.out.println(String.format(OUTPUT_FORMAT, "Input (plain text)", pText));
        System.out.println(String.format(OUTPUT_FORMAT, "Encrypted (base64) ", encryptedTextBase64));

        System.out.println("\n------ AES GCM Password-based Decryption ------");
        System.out.println(String.format(OUTPUT_FORMAT, "Input (base64)", encryptedTextBase64));

        String decryptedText = decrypt(encryptedTextBase64, PASSWORD);
        System.out.println(String.format(OUTPUT_FORMAT, "Decrypted (plain text)", decryptedText));
        /*
        String password = "password123";
        String fromFile = "readme.txt"; // from resources folder
        String toFile = "c:\\test\\readme.encrypted.txt";
        // encrypt file
        //EncryptorAesGcmPasswordFile.encryptFile(fromFile, toFile, password);
        // decrypt file
        byte[] decryptedBytes = decryptFile(toFile, password);
        String fileText = new String(decryptedBytes, UTF_8);
        System.out.println(fileText);
        */
    }
}
