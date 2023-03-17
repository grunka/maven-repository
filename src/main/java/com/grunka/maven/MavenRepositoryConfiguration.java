package com.grunka.maven;

import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String remoteRepositoryDirectory;
    @NotNull
    public Map<String, String> remoteRepositories;
}
