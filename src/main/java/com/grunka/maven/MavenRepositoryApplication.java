package com.grunka.maven;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

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

        Path remoteRepositoryDirectory = Path.of(configuration.remoteRepositoryDirectory);
        Files.createDirectories(remoteRepositoryDirectory);
        if (!Files.isWritable(remoteRepositoryDirectory)) {
            throw new IllegalStateException(remoteRepositoryDirectory + " is not writable");
        }
        LinkedHashMap<String, URI> remoteRepositories = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuration.remoteRepositories.entrySet()) {
            remoteRepositories.put(entry.getKey(), URI.create(entry.getValue()));
        }

        environment.jersey().register(new MavenRepositoryResource(remoteRepositoryDirectory, remoteRepositories));
    }
}
