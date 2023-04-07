package com.grunka.maven;

import com.grunka.maven.authentication.Access;
import io.dropwizard.core.Configuration;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class MavenRepositoryConfiguration extends Configuration {
    @NotNull
    public String storageDirectory;
    @NotNull
    public LinkedHashMap<String, Repository> remoteRepositories;
    @NotNull
    public Access defaultAccess;
    public Map<Access, Map<String, String>> users = null;
    public String sqliteDatabase = null;
    public int saltBits = 128;
    public int iterationCount = 500_000;
    public int keyLength = 512;
}
