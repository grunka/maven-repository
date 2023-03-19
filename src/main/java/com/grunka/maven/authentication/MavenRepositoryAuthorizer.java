package com.grunka.maven.authentication;

import io.dropwizard.auth.Authorizer;

public class MavenRepositoryAuthorizer implements Authorizer<MavenRepositoryUser> {
    @Override
    public boolean authorize(MavenRepositoryUser principal, String role) {
        MavenRepositoryUserLevel level = MavenRepositoryUserLevel.valueOf(role);
        return principal.getLevel().compareTo(level) >= 0;
    }
}
