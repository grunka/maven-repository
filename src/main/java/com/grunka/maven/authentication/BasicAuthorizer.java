package com.grunka.maven.authentication;

import io.dropwizard.auth.Authorizer;

public class BasicAuthorizer implements Authorizer<User> {
    @Override
    public boolean authorize(User principal, String role) {
        Access level = Access.valueOf(role);
        return principal.getLevel().compareTo(level) >= 0;
    }
}
