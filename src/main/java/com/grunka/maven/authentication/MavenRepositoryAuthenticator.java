package com.grunka.maven.authentication;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Optional;

public class MavenRepositoryAuthenticator implements Authenticator<BasicCredentials, MavenRepositoryUser> {
    private final String defaultAccess;

    public MavenRepositoryAuthenticator(String defaultAccess) {
        this.defaultAccess = defaultAccess;
    }

    @Override
    public Optional<MavenRepositoryUser> authenticate(BasicCredentials credentials) {
        if (MavenRepositoryDefaultUserFilter.DEFAULT_USERNAME.equals(credentials.getUsername()) && MavenRepositoryDefaultUserFilter.DEFAULT_PASSWORD.equals(credentials.getPassword())) {
            return Optional.of(new MavenRepositoryUser(MavenRepositoryDefaultUserFilter.DEFAULT_USERNAME, MavenRepositoryUserLevel.from(defaultAccess)));
        }
        return Optional.empty();
    }
}
