package com.grunka.maven;

import com.grunka.maven.authentication.User;
import com.grunka.maven.authentication.Access;
import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
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
@PermitAll
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
            throw new WebApplicationException(Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity("Invalid path")
                    .build());
        }
        return artifactPath;
    }

    private CompletableFuture<Response> getRepositoryContent(String path, boolean includeBody) {
        List<java.nio.file.Path> localFiles = new ArrayList<>();
        java.nio.file.Path localRepositoryFile = resolveStorageDirectory(LOCAL, path);
        if (isMavenMetadata(path)) {
            return CompletableFuture.completedFuture(notFound());
        }
        boolean isSnapshotVersion = localRepositoryFile.getParent().getFileName().endsWith("-SNAPSHOT");
        localFiles.add(localRepositoryFile);
        if (!isSnapshotVersion) {
            remoteRepositories.keySet().forEach(remote -> localFiles.add(resolveStorageDirectory(remote, path)));
        }
        Optional<java.nio.file.Path> localFile = localFiles.stream().filter(Files::exists).findFirst();
        if (localFile.isEmpty()) {
            if (isSnapshotVersion) {
                return CompletableFuture.completedFuture(notFound());
            }
            List<FileRequest> requests = new ArrayList<>();
            for (Map.Entry<String, URI> entry : remoteRepositories.entrySet()) {
                requests.add(new FileRequest(entry.getKey(), entry.getValue().resolve(path)));
            }

            return getRemoteFile(path, requests)
                    .thenCompose(file -> {
                        if (file == null) {
                            return CompletableFuture.completedFuture(notFound());
                        }
                        return createFileContentResponse(file, includeBody);
                    })
                    .exceptionally(t -> Response
                            .status(Response.Status.INTERNAL_SERVER_ERROR)
                            .type(MediaType.TEXT_PLAIN_TYPE)
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

    private static Response notFound() {
        return Response
                .status(Response.Status.NOT_FOUND)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("Not found")
                .build();
    }

    private static boolean isMavenMetadata(String path) {
        return path.endsWith("maven-metadata.xml") || path.endsWith("maven-metadata.xml.sha1") || path.endsWith("maven-metadata.xml.md5");
    }

    @HEAD
    @Path("/{path:.+}")
    public CompletableFuture<Response> head(@PathParam("path") String path, @Auth User user) {
        assertUserLevel(user, Access.read);
        if (isMavenMetadata(path)) {
            return CompletableFuture.completedFuture(notFound());
        }
        return getRepositoryContent(path, false);
    }

    @OPTIONS
    @Path("/{path:.+}")
    public Response options(@PathParam("path") String path, @Auth User user) {
        assertUserLevel(user, Access.read);
        if (isMavenMetadata(path)) {
            return notFound();
        }
        if (user.getLevel().compareTo(Access.write) < 0) {
            return Response
                    .status(Response.Status.NO_CONTENT)
                    .header("Allow", "OPTIONS, HEAD, GET")
                    .build();
        }
        return Response
                .status(Response.Status.NO_CONTENT)
                .header("Allow", "OPTIONS, HEAD, GET, PUT")
                .build();
    }

    @GET
    @Path("/{path:.+}")
    public CompletableFuture<Response> get(@PathParam("path") String path, @Auth User user) {
        //TODO add file listing if no file is being accessed?
        assertUserLevel(user, Access.read);
        if (isMavenMetadata(path)) {
            return CompletableFuture.completedFuture(notFound());
        }
        return getRepositoryContent(path, true);
    }

    @PUT
    @Path("/{path:.+}")
    public Response put(@PathParam("path") String path, InputStream contentStream, @Auth User user) {
        assertUserLevel(user, Access.write);
        byte[] content;
        try {
            content = contentStream.readAllBytes();
        } catch (IOException e) {
            LOG.error("Failed to read PUT content for path {}", path, e);
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity("Failed to read content")
                    .build();
        }
        java.nio.file.Path savePath = resolveStorageDirectory(LOCAL, path);
        if (isMavenMetadata(path)) {
            //TODO save files in tmp temporarily, then move them to
            if (path.endsWith(".xml")) {
                Optional<MavenMetadata> mavenMetadata = parseXmlDocument(content).map(d -> mapDocumentToRecord(d, MavenMetadata.class));
                LOG.debug("maven-metadata.xml: {}", new String(content));
            }
            return Response.ok().build();
        }
        String fileName = savePath.getFileName().toString();
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
                        .type(MediaType.TEXT_PLAIN_TYPE)
                        .entity("Not allowed to update released file")
                        .build();
            } else {
                return saveContent(path, new FileContent(savePath, content, Instant.now()), Response.Status.CREATED);
            }
        }
    }


    private record MavenMetadata(String groupId, String artifactId, String version, Versioning versioning) {
        private record Versioning(List<SnapshotVersion> snapshotVersions) {
            private record SnapshotVersion(String extension, String value, String updated) {
            }
        }
    }

    private <T> T mapDocumentToRecord(Document document, Class<T> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("The type needs to be a record");
        }
        //TODO complete this conversion thing
        Node rootNode = document.getChildNodes().item(0);
        return null;
    }

    private static Optional<Document> parseXmlDocument(byte[] content) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(content));
            document.getDocumentElement().normalize();
            return Optional.of(document);
        } catch (ParserConfigurationException e) {
            LOG.error("Failed to configure xml parser");
            return Optional.empty();
        } catch (SAXException e) {
            LOG.error("Failed to xml", e);
            return Optional.empty();
        } catch (IOException e) {
            LOG.error("Failed to read input stream", e);
            return Optional.empty();
        }
    }

    private static void assertUserLevel(User user, Access level) {
        if (user.getLevel().compareTo(level) < 0) {
            throw new WebApplicationException(unauthorized());
        }
    }

    private static Response unauthorized() {
        return Response
                .status(Response.Status.UNAUTHORIZED)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"maven-repository\"")
                .build();
    }

    private Response saveContent(String path, FileContent fileContent, Response.Status statusCode) {
        //TODO validate hashes somehow, maybe do when metadata is put since that is "after". Also clean up "other" snapshot files that weren't uploaded "now"
        try {
            Files.createDirectories(fileContent.path().getParent());
            Files.write(fileContent.path(), fileContent.content());
            Files.setLastModifiedTime(fileContent.path(), fileContent.lastModified());
        } catch (IOException e) {
            LOG.error("Failed to save file {}", fileContent.path(), e);
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN_TYPE)
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
