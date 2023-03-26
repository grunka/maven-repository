package com.grunka.maven;

import com.grunka.maven.authentication.BasicAuthenticator;
import com.grunka.maven.authentication.BasicAuthorizer;
import com.grunka.maven.authentication.DefaultUserFilter;
import com.grunka.maven.authentication.PasswordValidator;
import com.grunka.maven.authentication.User;
import com.grunka.maven.authentication.UserDAO;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

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
        //TODO figure out some health checks

        environment.jersey().register(DefaultUserFilter.class);
        UserDAO userDAO = new UserDAO(storageDirectory.resolve("users.sqlite"), new PasswordValidator(configuration.saltBits, configuration.iterationCount, configuration.keyLength));
        environment.jersey().register(new AuthDynamicFeature(
                new BasicCredentialAuthFilter.Builder<User>()
                        .setAuthenticator(new BasicAuthenticator(configuration.defaultAccess, configuration.users, userDAO))
                        .setAuthorizer(new BasicAuthorizer())
                        .setRealm("maven-repository")
                        .buildAuthFilter()));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));

        environment.jersey().register(new MavenRepositoryResource(storageDirectory, remoteRepositories));
    }
}
