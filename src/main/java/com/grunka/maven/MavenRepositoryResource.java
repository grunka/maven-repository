package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
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
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Path("/repository")
public class MavenRepositoryResource {
    private static final List<String> ACCEPTABLE_SUFFIXES = Stream.of(".jar", ".pom").flatMap(suffix -> Stream.of(suffix, suffix + ".md5", suffix + ".sha1")).toList();
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryResource.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    static final String LOCAL = "local";
    private final java.nio.file.Path storageDirectory;
    private final LinkedHashMap<String, URI> remoteRepositories;
    private final Map<java.nio.file.Path, SoftReference<CompletableFuture<FileContent>>> fileCache = new ConcurrentHashMap<>();

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
        if (localRepositoryFile.getFileName().startsWith("maven-metadata.xml")) {
            //TODO maybe generate file if needed
            return CompletableFuture.completedFuture(notFound(path));
        }
        boolean isSnapshotVersion = localRepositoryFile.getParent().getFileName().endsWith("-SNAPSHOT");
        localFiles.add(localRepositoryFile);
        if (!isSnapshotVersion) {
            remoteRepositories.keySet().forEach(remote -> localFiles.add(resolveStorageDirectory(remote, path)));
        }
        Optional<java.nio.file.Path> localFile = localFiles.stream().filter(Files::exists).findFirst();
        if (localFile.isEmpty()) {
            if (isSnapshotVersion) {
                return CompletableFuture.completedFuture(notFound(path));
            }
            List<FileRequest> requests = new ArrayList<>();
            for (Map.Entry<String, URI> entry : remoteRepositories.entrySet()) {
                requests.add(new FileRequest(entry.getKey(), entry.getValue().resolve(path)));
            }

            return getRemoteFile(path, requests)
                    .thenCompose(file -> {
                        if (file == null) {
                            return CompletableFuture.completedFuture(notFound(path));
                        }
                        return createFileContentResponse(file, includeBody);
                    })
                    .exceptionally(t -> Response
                            .status(Response.Status.INTERNAL_SERVER_ERROR)
                            .header("Content-Type", MediaType.TEXT_PLAIN)
                            .entity(t.getMessage())
                            .build()
                    );
        } else {
            LOG.info("Reading {} locally", path);
            return createFileContentResponse(localFile.get(), includeBody);
        }
    }

    private CompletableFuture<java.nio.file.Path> getRemoteFile(String path, List<FileRequest> requests) {
        if (requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        FileRequest fileRequest = requests.remove(0);
        HttpRequest httpRequest = HttpRequest.newBuilder().GET().uri(fileRequest.uri()).build();
        java.nio.file.Path targetFile = resolveStorageDirectory(fileRequest.repository(), path);
        LOG.info("Downloading {} from remote {}", path, fileRequest.uri());
        return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        LOG.error("Got status code {} from {}", response.statusCode(), fileRequest.uri());
                        return getRemoteFile(path, requests);
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
                        return CompletableFuture.failedFuture(new IllegalStateException("Cannot save file locally"));
                    } finally {
                        fileCache.remove(targetFile);
                    }
                    return CompletableFuture.completedFuture(targetFile);
                });
    }

    private CompletableFuture<Response> createFileContentResponse(java.nio.file.Path targetFile, boolean includeBody) {
        return getCachedFileContent(targetFile).thenApply(fileContent -> {
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
                    .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(fileContent.lastModified().toInstant().atZone(ZoneId.of("UTC"))))
                    .header("Etag", "\"" + fileContent.sha1() + "\"");
            if (includeBody) {
                responseBuilder = responseBuilder.entity(fileContent.content());
            }
            return responseBuilder.build();
        });
    }

    private CompletableFuture<FileContent> getCachedFileContent(java.nio.file.Path targetFile) {
        CompletableFuture<FileContent> fileContentFuture;
        do {
            fileContentFuture = fileCache.compute(targetFile, (f, reference) -> {
                if (reference != null && reference.get() != null) {
                    return reference;
                }
                return new SoftReference<>(CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] content = Files.readAllBytes(targetFile);
                        return new FileContent(targetFile, content, Files.getLastModifiedTime(targetFile));
                    } catch (IOException e) {
                        LOG.error("Could not read {}", targetFile, e);
                        throw new IllegalStateException("Could not read file");
                    }
                }));
            }).get();
        } while (fileContentFuture == null);
        return fileContentFuture;
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
    @RolesAllowed({"read", "write"})
    public CompletableFuture<Response> head(@PathParam("path") String path) {
        return getRepositoryContent(path, false);
    }

    @GET
    @Path("/{path:.+}")
    @RolesAllowed({"read", "write"})
    public CompletableFuture<Response> get(@PathParam("path") String path, @Context HttpServletRequest request) {
        //TODO add file listing if no file is being accessed
        return getRepositoryContent(path, true);
    }

    @PUT
    @Path("/{path:.+}")
    @RolesAllowed("write")
    public Response put(@PathParam("path") String path, InputStream contentStream, @Context HttpServletRequest request) {
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
                    return saveContent(path, new FileContent(savePath, content, Instant.now()), Response.Status.OK);
                } else {
                    return saveContent(path, new FileContent(savePath, content, Instant.now()), Response.Status.CREATED);
                }
            } else {
                if (Files.exists(savePath)) {
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .header("Content-Type", MediaType.TEXT_PLAIN)
                            .entity("Not allowed to update released file")
                            .build();
                } else {
                    return saveContent(path, new FileContent(savePath, content, Instant.now()), Response.Status.CREATED);
                }
            }
        }
        return Response.ok().build();
    }

    private Response saveContent(String path, FileContent fileContent, Response.Status statusCode) {
        try {
            Files.createDirectories(fileContent.path().getParent());
            Files.write(fileContent.path(), fileContent.content());
            Files.setLastModifiedTime(fileContent.path(), fileContent.lastModified());
        } catch (IOException e) {
            LOG.error("Failed to save file {}", fileContent.path(), e);
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", MediaType.TEXT_PLAIN)
                    .entity("Failed to save content")
                    .build();
        } finally {
            fileCache.remove(fileContent.path());
        }
        LOG.info("Saved path {} to {}", path, fileContent.path());
        return Response
                .status(statusCode)
                .header("Content-Location", "/repository/" + path)
                .build();
    }
}
