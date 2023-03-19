package com.grunka.maven;

import com.grunka.maven.authentication.MavenRepositoryUserLevel;
import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String storageDirectory;
    @NotNull
    public Map<String, String> remoteRepositories;
    @NotNull
    public MavenRepositoryUserLevel defaultAccess;
    @NotNull
    public Map<MavenRepositoryUserLevel, Map<String, String>> users;
    //TODO make it possible to have some other user source than the configuration
}
