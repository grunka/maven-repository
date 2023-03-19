package com.grunka.maven.authentication;

import java.security.Principal;

public class MavenRepositoryUser implements Principal {
    private final String name;
    private final MavenRepositoryUserLevel level;

    public MavenRepositoryUser(String name, MavenRepositoryUserLevel level) {
        this.name = name;
        this.level = level;
    }

    @Override
    public String getName() {
        return name;
    }

    public MavenRepositoryUserLevel getLevel() {
        return level;
    }
}
