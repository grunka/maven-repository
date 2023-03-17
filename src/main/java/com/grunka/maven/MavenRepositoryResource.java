package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Path("/repository")
public class MavenRepositoryResource {
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryResource.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private final java.nio.file.Path remoteRepositoryDirectory;
    private final LinkedHashMap<String, URI> remoteRepositories;

    public MavenRepositoryResource(java.nio.file.Path remoteRepositoryDirectory, LinkedHashMap<String, URI> remoteRepositories) {
        this.remoteRepositoryDirectory = remoteRepositoryDirectory;
        this.remoteRepositories = remoteRepositories;
    }

    private CompletableFuture<Response> getRepositoryContent(String path, boolean includeBody) {
        java.nio.file.Path targetFile = remoteRepositoryDirectory.resolve(path).toAbsolutePath();
        if (!targetFile.toAbsolutePath().startsWith(remoteRepositoryDirectory.toAbsolutePath())) {
            LOG.error("Target file {} not inside of directory {}", targetFile, remoteRepositoryDirectory);
            return CompletableFuture.completedFuture(notFound());
        }
        //TODO check if release or snapshot, if snapshot read from remote if "timed out". Check timestamp of file
        //TODO multiple remotes, retry, download first, put in different repos
        //TODO <storage>/<repository>/<snapshot/releases>
        //TODO authentication
        if (!Files.exists(targetFile)) {
            LOG.info("Downloading {} from remote", path);
            List<HttpRequest> httpRequests = new ArrayList<>(remoteRepositories.values().stream()
                    .map(uri -> uri.resolve(path))
                    .map(uri -> HttpRequest.newBuilder().GET().uri(uri).build())
                    .toList());
            return HTTP_CLIENT.sendAsync(httpRequests.remove(0), HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() == 404) {
                            throw new IllegalStateException("Not found");
                        }
                        Optional<ZonedDateTime> lastModified = response.headers()
                                .firstValue("Last-Modified")
                                .map(t -> ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME));
                        byte[] fileContent = response.body();
                        try {
                            Files.createDirectories(targetFile.getParent());
                            Files.write(targetFile, fileContent);
                            if (lastModified.isPresent()) {
                                Files.setLastModifiedTime(targetFile, FileTime.from(lastModified.get().toInstant()));
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to save file content for {}", targetFile);
                        }
                    })
                    .thenCompose(x -> createFileContentResponse(targetFile, includeBody))
                    .exceptionally(t -> notFound());
        } else {
            LOG.info("Reading {} locally", path);
            return createFileContentResponse(targetFile, includeBody);
        }
    }

    private static CompletableFuture<Response> createFileContentResponse(java.nio.file.Path targetFile, boolean includeBody) {
        byte[] content;
        FileTime lastModifiedTime;
        try {
            content = Files.readAllBytes(targetFile);
            lastModifiedTime = Files.getLastModifiedTime(targetFile);
        } catch (IOException e) {
            LOG.error("Could not read {}", targetFile, e);
            return CompletableFuture.completedFuture(notFound());
        }
        String filename = targetFile.getFileName().toString();
        String contentType = switch (filename.substring(filename.lastIndexOf('.'))) {
            case ".jar" -> "application/java-archive";
            case ".sha1" -> MediaType.TEXT_PLAIN;
            case ".pom" -> MediaType.TEXT_XML;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        Response.ResponseBuilder responseBuilder = Response
                .ok()
                .header("Content-Type", contentType)
                .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModifiedTime.toInstant().atZone(ZoneId.of("UTC"))))
                .header("Etag", "\"" + sha1(content) + "\"");
        if (includeBody) {
            responseBuilder = responseBuilder.entity(content);
        }
        return CompletableFuture.completedFuture(responseBuilder.build());
    }

    private static String sha1(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            return new BigInteger(1, messageDigest.digest(content)).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("SHA-1 did not exist", e);
        }
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @HEAD
    @Path("/{path:.+}")
    public CompletableFuture<Response> head(@PathParam("path") String path) {
        return getRepositoryContent(path, false);
    }

    @GET
    @Path("/{path:.+}")
    public CompletableFuture<Response> get(@PathParam("path") String path) {
        return getRepositoryContent(path, true);
    }

    @PUT
    @Path("/{path:.+}")
    public Response put(@PathParam("path") String path, InputStream contentStream) throws IOException {
        byte[] content = contentStream.readAllBytes();
        //TODO save content in snapshot or release locally
        //TODO block upload of existing releases
        return Response.ok().build();
    }
}
