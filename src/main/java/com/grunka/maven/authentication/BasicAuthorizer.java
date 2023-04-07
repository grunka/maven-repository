package com.grunka.maven.authentication;

import io.dropwizard.auth.Authorizer;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.checkerframework.checker.nullness.qual.Nullable;

public class BasicAuthorizer implements Authorizer<User> {
    @Override
    public boolean authorize(User principal, String role, @Nullable ContainerRequestContext requestContext) {
        Access level = Access.valueOf(role);
        return principal.getAccess().compareTo(level) >= 0;
    }
}
