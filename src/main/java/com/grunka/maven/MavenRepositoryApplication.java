package com.grunka.maven;

import com.codahale.metrics.health.HealthCheck;
import com.grunka.maven.authentication.Access;
import com.grunka.maven.authentication.BasicAuthenticator;
import com.grunka.maven.authentication.BasicAuthorizer;
import com.grunka.maven.authentication.DefaultUserFilter;
import com.grunka.maven.authentication.PasswordValidator;
import com.grunka.maven.authentication.User;
import com.grunka.maven.authentication.UserAuthenticator;
import com.grunka.maven.authentication.UserDAO;
import io.dropwizard.core.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.core.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Console;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class MavenRepositoryApplication extends Application<MavenRepositoryConfiguration> {
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryApplication.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            if ("create-database".equals(args[0])) {
                Path databaseLocation = Path.of(args[1]);
                if (Files.exists(databaseLocation)) {
                    LOG.error("Database file already exists {}", databaseLocation);
                    System.exit(1);
                }
                if (!Files.isWritable(databaseLocation)) {
                    LOG.error("Database file location is not writable {}", databaseLocation);
                    System.exit(1);
                }
                UserDAO.createDatabase(databaseLocation);
                LOG.info("Database created at {}", databaseLocation);
                System.exit(0);
            }
            if ("add-user".equals(args[0])) {
                Path databaseLocation = Path.of(args[1]);
                if (!Files.exists(databaseLocation)) {
                    LOG.error("Could not find database at {}", databaseLocation);
                    System.exit(1);
                }
                MavenRepositoryConfiguration defaultConfiguration = new MavenRepositoryConfiguration();
                PasswordValidator passwordValidator = new PasswordValidator(defaultConfiguration.saltBits, defaultConfiguration.iterationCount, defaultConfiguration.keyLength);
                Console console = System.console();
                if (console == null) {
                    LOG.error("Not able to read from console");
                    System.exit(1);
                }
                String username = console.readLine("Username: ");
                String password = new String(console.readPassword("Password: "));
                String possibleAccess = Arrays.stream(Access.values()).map(Objects::toString).collect(Collectors.joining(", "));
                Optional<Access> access = Optional.empty();
                do {
                    String accessInput = console.readLine("Access(" + possibleAccess + "): ");
                    try {
                        access = Optional.of(Access.valueOf(accessInput));
                    } catch (IllegalArgumentException e) {
                        LOG.error("{} is not a valid access level", accessInput);
                    }
                } while (access.isEmpty());
                UserDAO userDAO = new UserDAO(databaseLocation, passwordValidator);
                userDAO.addUser(username, password, access.get());
                LOG.info("User {} added in {}", username, databaseLocation);
                System.exit(0);
            }
        }
        new MavenRepositoryApplication().run(args);
    }

    @Override
    public void run(MavenRepositoryConfiguration configuration, Environment environment) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Path storageDirectory = Path.of(configuration.storageDirectory);
        Files.createDirectories(storageDirectory);
        if (!Files.isWritable(storageDirectory)) {
            throw new IllegalStateException(storageDirectory + " is not writable");
        }
        LinkedHashMap<String, URI> remoteRepositories = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuration.remoteRepositories.entrySet()) {
            if (MavenRepositoryResource.LOCAL.equals(entry.getKey())) {
                throw new IllegalArgumentException("The name 'local' is reserved for the local repository");
            }
            remoteRepositories.put(entry.getKey(), URI.create(entry.getValue()));
        }
        environment.jersey().register(new IndexResource());

        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        environment.healthChecks().register("uptime", new HealthCheck() {
            @Override
            protected Result check() {
                return Result.healthy(String.valueOf(Duration.between(startedAt, Instant.now()).toSeconds()));
            }
        });
        environment.healthChecks().register("startedAt", new HealthCheck() {
            @Override
            protected Result check() {
                return Result.healthy(startedAt.toString());
            }
        });

        configureAuthentication(configuration, environment, storageDirectory.resolve("users.sqlite"));

        environment.jersey().register(new MavenRepositoryResource(storageDirectory, remoteRepositories));
    }

    private static void configureAuthentication(MavenRepositoryConfiguration configuration, Environment environment, Path userDatabaseLocation) {
        environment.jersey().register(DefaultUserFilter.class);
        List<UserAuthenticator> authenticators = new ArrayList<>();
        authenticators.add((username, password) -> {
            if (DefaultUserFilter.DEFAULT_USERNAME.equals(username) && DefaultUserFilter.DEFAULT_PASSWORD.equals(password)) {
                return Optional.of(new User(DefaultUserFilter.DEFAULT_USERNAME, configuration.defaultAccess));
            }
            return Optional.empty();
        });
        if (configuration.users != null) {
            authenticators.add((username, password) -> {
                for (Map.Entry<Access, Map<String, String>> entry : configuration.users.entrySet()) {
                    Map<String, String> logins = entry.getValue() != null ? entry.getValue() : Map.of();
                    if (password.equals(logins.get(username))) {
                        return Optional.of(new User(username, entry.getKey()));
                    }
                }
                return Optional.empty();
            });
        }
        if (configuration.sqliteDatabase != null) {
            UserDAO userDAO = new UserDAO(Path.of(configuration.sqliteDatabase), new PasswordValidator(configuration.saltBits, configuration.iterationCount, configuration.keyLength));
            authenticators.add(userDAO::authenticate);
        }
        environment.jersey().register(new AuthDynamicFeature(
                new BasicCredentialAuthFilter.Builder<User>()
                        .setAuthenticator(new BasicAuthenticator(authenticators))
                        .setAuthorizer(new BasicAuthorizer())
                        .setRealm("maven-repository")
                        .buildAuthFilter()));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
    }
}
