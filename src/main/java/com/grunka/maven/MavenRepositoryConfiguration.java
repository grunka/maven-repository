package com.grunka.maven;

import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String remoteRepositoryDirectory;
    @NotNull
    public String centralRepository;
}
