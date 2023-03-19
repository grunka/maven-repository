package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Path("/repository")
public class MavenRepositoryResource {
    private static final List<String> ACCEPTABLE_SUFFIXES = Stream.of(".jar", ".pom").flatMap(suffix -> Stream.of(suffix, suffix + ".md5", suffix + ".sha1")).toList();
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryResource.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String LOCAL = "local";
    private final java.nio.file.Path storageDirectory;
    private final LinkedHashMap<String, URI> remoteRepositories;

    public MavenRepositoryResource(java.nio.file.Path storageDirectory, LinkedHashMap<String, URI> remoteRepositories) {
        this.storageDirectory = storageDirectory;
        this.remoteRepositories = remoteRepositories;
    }

    private record FileRequest(String repository, URI uri) {
    }

    private java.nio.file.Path resolveStorageDirectory(String repository, String path) {
        java.nio.file.Path absoluteRepositoryPath = storageDirectory.resolve(repository).toAbsolutePath();
        java.nio.file.Path artifactPath = storageDirectory.resolve(repository).resolve(path).toAbsolutePath();
        if (!artifactPath.startsWith(absoluteRepositoryPath)) {
            LOG.error("Path {} did not resolve inside of {}", path, absoluteRepositoryPath);
            throw new IllegalArgumentException("Path did not end up inside repository");
        }
        return artifactPath;
    }

    private CompletableFuture<Response> getRepositoryContent(String path, boolean includeBody) {
        List<java.nio.file.Path> localFiles = new ArrayList<>();
        java.nio.file.Path localRepositoryFile = resolveStorageDirectory(LOCAL, path);
        boolean isSnapshotVersion = localRepositoryFile.getParent().getFileName().endsWith("-SNAPSHOT");
        localFiles.add(localRepositoryFile);
        if (!isSnapshotVersion) {
            remoteRepositories.keySet().forEach(remote -> localFiles.add(resolveStorageDirectory(remote, path)));
        }
        //TODO authentication
        Optional<java.nio.file.Path> localFile = localFiles.stream().filter(Files::exists).findFirst();
        if (localFile.isEmpty()) {
            if (isSnapshotVersion) {
                return CompletableFuture.completedFuture(notFound(path));
            }
            List<FileRequest> requests = new ArrayList<>();
            for (Map.Entry<String, URI> entry : remoteRepositories.entrySet()) {
                requests.add(new FileRequest(entry.getKey(), entry.getValue().resolve(path)));
            }

            //TODO multiple remotes, retry, download first, put in different repos
            FileRequest fileRequest = requests.remove(0);
            HttpRequest httpRequest = HttpRequest.newBuilder().GET().uri(fileRequest.uri()).build();
            java.nio.file.Path targetFile = storageDirectory.resolve(fileRequest.repository()).resolve(path);
            LOG.info("Downloading {} from remote {}", path, fileRequest.uri());
            return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
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
                    .exceptionally(t -> notFound(path));
        } else {
            LOG.info("Reading {} locally", path);
            return createFileContentResponse(localFile.get(), includeBody);
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
            return CompletableFuture.completedFuture(Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", MediaType.TEXT_PLAIN)
                    .entity("Could not read file")
                    .build()
            );
        }
        String filename = targetFile.getFileName().toString();
        String contentType = switch (filename.substring(filename.lastIndexOf('.'))) {
            case ".jar" -> "application/java-archive";
            case ".sha1", ".md5" -> MediaType.TEXT_PLAIN;
            case ".xml", ".pom" -> MediaType.TEXT_XML;
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

    private static String md5(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD-5");
            return new BigInteger(1, messageDigest.digest(content)).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("MD-5 did not exist", e);
        }
    }

    private static Response notFound(String missingPath) {
        LOG.error("Could not read path {}", missingPath);
        return Response
                .status(Response.Status.NOT_FOUND)
                .header("Content-Type", MediaType.TEXT_PLAIN)
                .entity("Not found")
                .build();
    }

    @HEAD
    @Path("/{path:.+}")
    public CompletableFuture<Response> head(@PathParam("path") String path) {
        return getRepositoryContent(path, false);
    }

    @GET
    @Path("/{path:.+}")
    public CompletableFuture<Response> get(@PathParam("path") String path, @Context HttpServletRequest request) {
        //TODO add file listing if no file is being accessed
        return getRepositoryContent(path, true);
    }

    @PUT
    @Path("/{path:.+}")
    public Response put(@PathParam("path") String path, InputStream contentStream) {
        //TODO validate hashes
        byte[] content;
        try {
            content = contentStream.readAllBytes();
        } catch (IOException e) {
            LOG.error("Failed to read PUT content for path {}", path, e);
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .header("Content-Type", MediaType.TEXT_PLAIN)
                    .entity("Failed to read content")
                    .build();
        }
        java.nio.file.Path savePath = resolveStorageDirectory(LOCAL, path);
        String fileName = savePath.getFileName().toString();
        if (fileName.startsWith("maven-metadata.xml")) {
            //TODO maybe use maven-metadata.xml to clean up / validate files?
        } else {
            Optional<String> acceptedSuffix = ACCEPTABLE_SUFFIXES.stream().filter(fileName::endsWith).findFirst();
            if (acceptedSuffix.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            String fileType = acceptedSuffix.get();
            String version = savePath.getParent().getFileName().toString();
            if (version.endsWith("-SNAPSHOT")) {
                String updatedFileName = fileName.replaceFirst("-\\d{8}\\.\\d{6}-\\d+(-[a-zA-Z]+)?" + fileType.replaceAll("\\.", "\\\\.") + "$", "-SNAPSHOT$1" + fileType);
                savePath = savePath.getParent().resolve(updatedFileName);
                if (Files.exists(savePath)) {
                    return saveContent(path, content, savePath, Response.Status.OK);
                } else {
                    return saveContent(path, content, savePath, Response.Status.CREATED);
                }
            } else {
                if (Files.exists(savePath)) {
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .header("Content-Type", MediaType.TEXT_PLAIN)
                            .entity("Not allowed to update released file")
                            .build();
                } else {
                    return saveContent(path, content, savePath, Response.Status.CREATED);
                }
            }
        }
        return Response.ok().build();
    }

    private static Response saveContent(String path, byte[] content, java.nio.file.Path savePath, Response.Status statusCode) {
        try {
            Files.createDirectories(savePath.getParent());
            Files.write(savePath, content);
        } catch (IOException e) {
            LOG.error("Failed to save file {}", savePath, e);
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", MediaType.TEXT_PLAIN)
                    .entity("Failed to save content")
                    .build();
        }
        LOG.info("Saved path {} to {}", path, savePath);
        return Response
                .status(statusCode)
                .header("Content-Location", "/repository/" + path)
                .build();
    }
}
