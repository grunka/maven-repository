package com.grunka.maven.authentication;

public enum MavenRepositoryUserLevel {
    none, read, write;

    public static MavenRepositoryUserLevel from(String name) {
        if ("read".equalsIgnoreCase(name)) {
            return read;
        }
        if ("write".equalsIgnoreCase(name)) {
            return write;
        }
        if ("none".equalsIgnoreCase(name)) {
            return none;
        }
        throw new IllegalArgumentException("Accepted levels are read and write");
    }
}
