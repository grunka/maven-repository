package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@Path("/repository")
public class MavenRepositoryResource {
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryResource.class);
    private final java.nio.file.Path remoteRepositoryDirectory;
    private final URI centralRepository;

    public MavenRepositoryResource(java.nio.file.Path remoteRepositoryDirectory, URI centralRepository) {
        this.remoteRepositoryDirectory = remoteRepositoryDirectory;
        this.centralRepository = centralRepository;
    }

    @HEAD
    @Path("/{path:.+}")
    public CompletableFuture<Response> head(@PathParam("path") String path) {
        //TODO only do the head part
        return getContent(path);
    }

    private CompletableFuture<Response> getContent(String path) {
        java.nio.file.Path targetFile = remoteRepositoryDirectory.resolve(path).toAbsolutePath();
        if (!targetFile.toAbsolutePath().startsWith(remoteRepositoryDirectory.toAbsolutePath())) {
            LOG.error("Target file {} not inside of directory {}", targetFile, remoteRepositoryDirectory);
            return CompletableFuture.completedFuture(Response.status(Response.Status.NOT_FOUND).build());
        }
        //TODO check if release or snapshot, if snapshot read from remote if "timed out". Check timestamp of file
        //TODO multiple remotes?
        //TODO authentication
        //TODO correct headers
        if (!Files.exists(targetFile)) {
            LOG.info("Downloading {} from remote", path);
            URI remotePath = centralRepository.resolve(path);
            HttpRequest httpRequest = HttpRequest.newBuilder().GET().uri(remotePath).build();
            return HttpClient.newHttpClient().sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> {
                        byte[] fileContent = response.body();
                        try {
                            Files.createDirectories(targetFile.getParent());
                            Files.write(targetFile, fileContent);
                        } catch (IOException e) {
                            LOG.error("Failed to save file content for {}", targetFile);
                        }
                        return Response.ok(fileContent).build();
                    });
        } else {
            LOG.info("Reading {} locally", path);
            try {
                //TODO memory cache for content
                return CompletableFuture.completedFuture(Response.ok(Files.readAllBytes(targetFile)).build());
            } catch (IOException e) {
                LOG.error("Failed to read file {}", targetFile);
            }
        }
        return CompletableFuture.completedFuture(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{path:.+}")
    public CompletableFuture<Response> get(@PathParam("path") String path) {
        return getContent(path);
    }

    @POST
    @Path("/{path:.+}")
    public Response post(@PathParam("path") String path) {
        //TODO deploy?
        return Response.ok().build();
    }
}
