package com.grunka.maven.authentication;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BasicAuthenticator implements Authenticator<BasicCredentials, User> {
    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthenticator.class);
    private final Access defaultAccess;
    private final Map<Access, Map<String, String>> users;
    private final UserDAO userDAO;
    private final Semaphore failedLoginLimiter = new Semaphore(1, true);
    private final Map<BasicCredentials, SoftReference<CompletableFuture<Optional<User>>>> authenticationCache = new ConcurrentHashMap<>();

    public BasicAuthenticator(Access defaultAccess, Map<Access, Map<String, String>> users, UserDAO userDAO) {
        this.defaultAccess = defaultAccess;
        this.users = users;
        this.userDAO = userDAO;
    }

    @Override
    public Optional<User> authenticate(BasicCredentials credentials) {
        CompletableFuture<Optional<User>> userFuture;
        do {
            userFuture = authenticationCache.compute(credentials, (c, reference) -> {
                if (reference != null && reference.get() != null) {
                    return reference;
                }
                return new SoftReference<>(CompletableFuture.supplyAsync(() -> authenticateCredentials(c)));
            }).get();
        } while (userFuture == null);
        Optional<User> user = userFuture.join();
        if (user.isPresent()) {
            return user;
        }
        failedLoginLimiter.acquireUninterruptibly();
        try {
            LOG.error("Failed login for {}", credentials);
            return Optional.empty();
        } finally {
            CompletableFuture.runAsync(failedLoginLimiter::release, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
        }
    }

    private Optional<User> authenticateCredentials(BasicCredentials credentials) {
        if (DefaultUserFilter.DEFAULT_USERNAME.equals(credentials.getUsername()) && DefaultUserFilter.DEFAULT_PASSWORD.equals(credentials.getPassword())) {
            return Optional.of(new User(DefaultUserFilter.DEFAULT_USERNAME, defaultAccess));
        }
        Optional<User> userFromDAO = userDAO.validate(credentials.getUsername(), credentials.getPassword());
        if (userFromDAO.isPresent()) {
            return userFromDAO;
        }
        for (Map.Entry<Access, Map<String, String>> entry : users.entrySet()) {
            Map<String, String> logins = entry.getValue() != null ? entry.getValue() : Map.of();
            if (credentials.getPassword().equals(logins.get(credentials.getUsername()))) {
                return Optional.of(new User(credentials.getUsername(), entry.getKey()));
            }
        }
        return Optional.empty();
    }
}
