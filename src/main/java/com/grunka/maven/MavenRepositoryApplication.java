package com.grunka.maven;

import com.codahale.metrics.health.HealthCheck;
import com.grunka.maven.authentication.*;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.core.Application;
import io.dropwizard.core.cli.Command;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
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
        new MavenRepositoryApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<MavenRepositoryConfiguration> bootstrap) {
        bootstrap.addCommand(new Command("create-database", "Creates a new sqlite database") {
            @Override
            public void configure(Subparser subparser) {
                subparser.addArgument("-f", "--file")
                        .dest("file")
                        .type(String.class)
                        .required(true)
                        .help("Location of the sqlite database file");
            }

            @Override
            public void run(Bootstrap<?> bootstrap, Namespace namespace) {
                Path databaseLocation = Path.of(namespace.getString("file"));
                if (Files.exists(databaseLocation)) {
                    LOG.error("Database file already exists {}", databaseLocation);
                    System.exit(1);
                }
                try {
                    UserDAO.createDatabase(databaseLocation);
                    LOG.info("Database created at {}", databaseLocation);
                    System.exit(0);
                } catch (Exception e) {
                    LOG.error("Failed to create database at {}", databaseLocation, e);
                    System.exit(1);
                }
            }
        });
        bootstrap.addCommand(new Command("add-user", "Add user to sqlite database") {
            @Override
            public void configure(Subparser subparser) {
                subparser.addArgument("-f", "--file")
                        .dest("file")
                        .type(String.class)
                        .required(true)
                        .help("Location of the sqlite database file");
            }

            @Override
            public void run(Bootstrap<?> bootstrap, Namespace namespace) {
                Path databaseLocation = Path.of(namespace.getString("file"));
                if (!Files.exists(databaseLocation)) {
                    LOG.error("Could not find database at {}", databaseLocation);
                    System.exit(1);
                }
                Console console = System.console();
                if (console == null) {
                    LOG.error("Not able to read from console");
                    System.exit(1);
                }
                String username = console.readLine("Username: ");
                String password = new String(console.readPassword("Password: "));
                Access access = readAccessFromConsole(console);
                MavenRepositoryConfiguration defaultConfiguration = new MavenRepositoryConfiguration();
                PasswordValidator passwordValidator = new PasswordValidator(defaultConfiguration.saltBits, defaultConfiguration.iterationCount, defaultConfiguration.keyLength);
                UserDAO userDAO = new UserDAO(databaseLocation, passwordValidator);
                if (userDAO.addUser(username, password, access)) {
                    LOG.info("User {} added in {}", username, databaseLocation);
                    System.exit(0);
                } else {
                    LOG.error("Failed to add user {}", username);
                    System.exit(1);
                }
            }
        });
        bootstrap.addCommand(new Command("set-user-password", "Set password for existing user in database") {
            @Override
            public void configure(Subparser subparser) {
                subparser.addArgument("-f", "--file")
                        .dest("file")
                        .type(String.class)
                        .required(true)
                        .help("Location of the sqlite database file");
            }

            @Override
            public void run(Bootstrap<?> bootstrap, Namespace namespace) {
                Path databaseLocation = Path.of(namespace.getString("file"));
                if (!Files.exists(databaseLocation)) {
                    LOG.error("Could not find database at {}", databaseLocation);
                    System.exit(1);
                }
                Console console = System.console();
                if (console == null) {
                    LOG.error("Not able to read from console");
                    System.exit(1);
                }
                String username = console.readLine("Username: ");
                String password = new String(console.readPassword("Password: "));
                MavenRepositoryConfiguration defaultConfiguration = new MavenRepositoryConfiguration();
                PasswordValidator passwordValidator = new PasswordValidator(defaultConfiguration.saltBits, defaultConfiguration.iterationCount, defaultConfiguration.keyLength);
                UserDAO userDAO = new UserDAO(databaseLocation, passwordValidator);
                if (userDAO.setPassword(username, password)) {
                    LOG.info("Password updated for user {}", username);
                    System.exit(0);
                } else {
                    LOG.error("Failed to update password for user {}", username);
                    System.exit(1);
                }
            }
        });
        bootstrap.addCommand(new Command("set-user-access", "Set access level for a user in the sqlite database") {
            @Override
            public void configure(Subparser subparser) {
                subparser.addArgument("-f", "--file")
                        .dest("file")
                        .type(String.class)
                        .required(true)
                        .help("Location of the sqlite database file");
            }

            @Override
            public void run(Bootstrap<?> bootstrap, Namespace namespace) {
                Path databaseLocation = Path.of(namespace.getString("file"));
                if (!Files.exists(databaseLocation)) {
                    LOG.error("Could not find database at {}", databaseLocation);
                    System.exit(1);
                }
                Console console = System.console();
                if (console == null) {
                    LOG.error("Not able to read from console");
                    System.exit(1);
                }
                String username = console.readLine("Username: ");
                Access access = readAccessFromConsole(console);
                MavenRepositoryConfiguration defaultConfiguration = new MavenRepositoryConfiguration();
                PasswordValidator passwordValidator = new PasswordValidator(defaultConfiguration.saltBits, defaultConfiguration.iterationCount, defaultConfiguration.keyLength);
                UserDAO userDAO = new UserDAO(databaseLocation, passwordValidator);
                if (userDAO.setAccess(username, access)) {
                    LOG.info("User access set to {} for user {}", access, username);
                    System.exit(0);
                } else {
                    LOG.error("Failed to update access to {} for user {}", access, username);
                    System.exit(1);
                }
            }
        });
    }

    private static Access readAccessFromConsole(Console console) {
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
        return access.get();
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

        configureAuthentication(configuration, environment);

        environment.jersey().register(new MavenRepositoryResource(storageDirectory, remoteRepositories));
    }

    private static void configureAuthentication(MavenRepositoryConfiguration configuration, Environment environment) {
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
