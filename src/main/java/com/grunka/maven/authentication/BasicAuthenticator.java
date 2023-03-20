package com.grunka.maven.authentication;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BasicAuthenticator implements Authenticator<BasicCredentials, User> {
    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthenticator.class);
    private final Access defaultAccess;
    private final Map<Access, Map<String, String>> users;
    private final Semaphore failedLoginLimiter = new Semaphore(1, true);

    public BasicAuthenticator(Access defaultAccess, Map<Access, Map<String, String>> users) {
        this.defaultAccess = defaultAccess;
        this.users = users;
    }

    @Override
    public Optional<User> authenticate(BasicCredentials credentials) {
        if (DefaultUserFilter.DEFAULT_USERNAME.equals(credentials.getUsername()) && DefaultUserFilter.DEFAULT_PASSWORD.equals(credentials.getPassword())) {
            return Optional.of(new User(DefaultUserFilter.DEFAULT_USERNAME, defaultAccess));
        }
        for (Map.Entry<Access, Map<String, String>> entry : users.entrySet()) {
            Map<String, String> logins = entry.getValue() != null ? entry.getValue() : Map.of();
            if (credentials.getPassword().equals(logins.get(credentials.getUsername()))) {
                return Optional.of(new User(credentials.getUsername(), entry.getKey()));
            }
        }
        failedLoginLimiter.acquireUninterruptibly();
        try {
            LOG.error("Failed login for {}", credentials);
            return Optional.empty();
        } finally {
            CompletableFuture.runAsync(failedLoginLimiter::release, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
        }
    }
}
