package com.grunka.maven;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

record FileContent(Path path, byte[] content, FileTime lastModified, String sha1, String md5) {
    public FileContent(Path path, byte[] content, Instant lastModified) {
        this(path, content, FileTime.from(lastModified), sha1(content), md5(content));
    }

    public FileContent(Path path, byte[] content, FileTime lastModified) {
        this(path, content, lastModified, sha1(content), md5(content));
    }

    private static String sha1(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            return new BigInteger(1, messageDigest.digest(content)).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("SHA-1 did not exist", e);
        }
    }

    private static String md5(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            return new BigInteger(1, messageDigest.digest(content)).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("MD5 did not exist", e);
        }
    }
}
