package com.grunka.maven;

import com.grunka.maven.authentication.Access;
import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String storageDirectory;
    @NotNull
    public Map<String, String> remoteRepositories;
    @NotNull
    public Access defaultAccess;
    public Map<Access, Map<String, String>> users = Map.of();
    public int saltBits = 128;
    public int iterationCount = 500_000;
    public int keyLength = 512;
}
