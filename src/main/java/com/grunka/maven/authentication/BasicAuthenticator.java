package com.grunka.maven.authentication;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BasicAuthenticator implements Authenticator<BasicCredentials, User> {
    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthenticator.class);
    private final List<UserAuthenticator> authenticators;
    private final Semaphore failedLoginLimiter = new Semaphore(1, true);
    private final Map<BasicCredentials, SoftReference<CompletableFuture<Optional<User>>>> authenticationCache = new ConcurrentHashMap<>();

    public BasicAuthenticator(List<UserAuthenticator> authenticators) {
        this.authenticators = authenticators;
    }

    @Override
    public Optional<User> authenticate(BasicCredentials credentials) {
        CompletableFuture<Optional<User>> userFuture;
        do {
            userFuture = authenticationCache.compute(credentials, (c, reference) -> {
                if (reference != null && reference.get() != null) {
                    return reference;
                }
                return new SoftReference<>(CompletableFuture.supplyAsync(() -> authenticateCredentials(c.getUsername(), c.getPassword())));
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

    private Optional<User> authenticateCredentials(String username, String password) {
        return authenticators.stream()
                .map(authenticator -> authenticator.authenticate(username, password))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
