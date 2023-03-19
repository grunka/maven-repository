package com.grunka.maven;

import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String storageDirectory;
    @NotNull
    public Map<String, String> remoteRepositories;

    //TODO authentication configuration?
    // users:
    //   <username>: <password>
    // access:
    //   <username>:
    //     read: <repo>
    //     write: <repo>
}
