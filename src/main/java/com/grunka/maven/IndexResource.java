package com.grunka.maven;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Path("/")
public class IndexResource {
    private static final Logger LOG = LoggerFactory.getLogger(IndexResource.class);
    private final Map<String, Optional<byte[]>> lazyLoadCache = new ConcurrentHashMap<>();
    private Optional<byte[]> getBytes(String resource) {
        return lazyLoadCache.computeIfAbsent(resource, r -> {
            try (InputStream resourceAsStream = getClass().getResourceAsStream(r)) {
                if (resourceAsStream == null) {
                    return Optional.empty();
                }
                return Optional.of(resourceAsStream.readAllBytes());
            } catch (IOException e) {
                LOG.error("Failed to read resource {}", r, e);
                return Optional.empty();
            }

        });
    }

    @GET
    @Path("/")
    public Response indexHtml() {
        return getBytes("/index.html")
                .map(b -> Response.ok(b).type(MediaType.TEXT_HTML_TYPE).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/pom.png")
    public Response pomPng() {
        return getBytes("/pom.png")
                .map(b -> Response.ok(b).type("image/png").build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
