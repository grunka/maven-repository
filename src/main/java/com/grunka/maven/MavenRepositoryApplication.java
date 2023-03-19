package com.grunka.maven;

import com.grunka.maven.authentication.MavenRepositoryDefaultUserFilter;
import com.grunka.maven.authentication.MavenRepositoryAuthenticator;
import com.grunka.maven.authentication.MavenRepositoryAuthorizer;
import com.grunka.maven.authentication.MavenRepositoryUser;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

public class MavenRepositoryApplication extends Application<MavenRepositoryConfiguration> {
    public static void main(String[] args) throws Exception {
        new MavenRepositoryApplication().run(args);
    }
    @Override
    public void run(MavenRepositoryConfiguration configuration, Environment environment) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        environment.jersey().register(JsonProvider.class);

        Path remoteRepositoryDirectory = Path.of(configuration.storageDirectory);
        Files.createDirectories(remoteRepositoryDirectory);
        if (!Files.isWritable(remoteRepositoryDirectory)) {
            throw new IllegalStateException(remoteRepositoryDirectory + " is not writable");
        }
        LinkedHashMap<String, URI> remoteRepositories = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuration.remoteRepositories.entrySet()) {
            if (MavenRepositoryResource.LOCAL.equals(entry.getKey())) {
                throw new IllegalArgumentException("The name 'local' is reserved for the local repository");
            }
            remoteRepositories.put(entry.getKey(), URI.create(entry.getValue()));
        }

        environment.jersey().register(MavenRepositoryDefaultUserFilter.class);
        environment.jersey().register(new AuthDynamicFeature(
                new BasicCredentialAuthFilter.Builder<MavenRepositoryUser>()
                        .setAuthenticator(new MavenRepositoryAuthenticator(configuration.defaultAccess))
                        .setAuthorizer(new MavenRepositoryAuthorizer())
                        .setRealm("grunka/maven-repository")
                        .buildAuthFilter()));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(MavenRepositoryUser.class));

        environment.jersey().register(new MavenRepositoryResource(remoteRepositoryDirectory, remoteRepositories));
    }
}
