package com.grunka.maven.authentication;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

public class PasswordValidator {
    private final int saltBits;
    private final int iterationCount;
    private final int keyLength;

    public PasswordValidator(int saltBits, int iterationCount, int keyLength) {
        this.saltBits = saltBits;
        this.iterationCount = iterationCount;
        this.keyLength = keyLength;
    }

    public boolean validate(String password, String hash) {
        String[] splits = hash.split(":");
        if (splits.length == 3) {
            byte[] salt = HexFormat.of().parseHex(splits[0]);
            int iterationCount = Integer.parseInt(splits[1], 16);
            String hashed = hash(salt, password, iterationCount);
            return hashed.equals(splits[2]);
        } else {
            return hash.equals(password);
        }
    }

    public boolean shouldUpdateHash(String passwordHash) {
        String[] splits = passwordHash.split(":");
        if (splits.length == 3) {
            if (HexFormat.of().parseHex(splits[0]).length != saltBits / 8) {
                return true;
            }
            if (Integer.parseInt(splits[1], 16) != iterationCount) {
                return true;
            }
            return splits[2].length() != keyLength / 8;
        } else {
            return true;
        }
    }

    public String createHash(String password) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[saltBits / 8];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt) + ":" + Integer.toString(iterationCount, 16) + ":" + hash(salt, password, iterationCount);
    }

    private String hash(byte[] salt, String password, int iterationCount) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new Error(e);
        }
    }
}
