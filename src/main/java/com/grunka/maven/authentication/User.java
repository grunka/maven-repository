package com.grunka.maven.authentication;

import java.security.Principal;

public class User implements Principal {
    private final String name;
    private final Access access;

    public User(String name, Access access) {
        this.name = name;
        this.access = access;
    }

    @Override
    public String getName() {
        return name;
    }

    public Access getAccess() {
        return access;
    }
}
