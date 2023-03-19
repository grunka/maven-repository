package com.grunka.maven.authentication;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Map;
import java.util.Optional;

public class MavenRepositoryAuthenticator implements Authenticator<BasicCredentials, MavenRepositoryUser> {
    private final MavenRepositoryUserLevel defaultAccess;
    private final Map<MavenRepositoryUserLevel, Map<String, String>> users;

    public MavenRepositoryAuthenticator(MavenRepositoryUserLevel defaultAccess, Map<MavenRepositoryUserLevel, Map<String, String>> users) {
        this.defaultAccess = defaultAccess;
        this.users = users;
    }

    @Override
    public Optional<MavenRepositoryUser> authenticate(BasicCredentials credentials) {
        if (MavenRepositoryDefaultUserFilter.DEFAULT_USERNAME.equals(credentials.getUsername()) && MavenRepositoryDefaultUserFilter.DEFAULT_PASSWORD.equals(credentials.getPassword())) {
            return Optional.of(new MavenRepositoryUser(MavenRepositoryDefaultUserFilter.DEFAULT_USERNAME, defaultAccess));
        }
        for (Map.Entry<MavenRepositoryUserLevel, Map<String, String>> entry : users.entrySet()) {
            Map<String, String> logins = entry.getValue() != null ? entry.getValue() : Map.of();
            if (credentials.getPassword().equals(logins.get(credentials.getUsername()))) {
                return Optional.of(new MavenRepositoryUser(credentials.getUsername(), entry.getKey()));
            }
        }
        return Optional.empty();
    }
}
