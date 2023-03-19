package com.grunka.maven.authentication;

public enum MavenRepositoryUserLevel {
    NONE, READ, WRITE;

    public static MavenRepositoryUserLevel from(String name) {
        if ("read".equalsIgnoreCase(name)) {
            return READ;
        }
        if ("write".equalsIgnoreCase(name)) {
            return WRITE;
        }
        if ("none".equalsIgnoreCase(name)) {
            return NONE;
        }
        throw new IllegalArgumentException("Accepted levels are read and write");
    }
}
