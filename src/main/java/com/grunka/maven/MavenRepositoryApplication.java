package com.grunka.maven;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

import java.util.TimeZone;

public class MavenRepositoryApplication extends Application<MavenRepositoryConfiguration> {
    public static void main(String[] args) throws Exception {
        new MavenRepositoryApplication().run(args);
    }
    @Override
    public void run(MavenRepositoryConfiguration configuration, Environment environment) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        environment.jersey().register(JsonProvider.class);
        environment.jersey().register(new MavenRepositoryResource());
    }
}
