package com.grunka.maven.authentication;

import java.security.Principal;

public class User implements Principal {
    private final String name;
    private final Access level;

    public User(String name, Access level) {
        this.name = name;
        this.level = level;
    }

    @Override
    public String getName() {
        return name;
    }

    public Access getLevel() {
        return level;
    }
}
