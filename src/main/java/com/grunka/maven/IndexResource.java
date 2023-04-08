package com.grunka.maven;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class IndexResource {
    private final ResourceLoader resourceLoader;

    public IndexResource(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GET
    @Path("/")
    public Response indexHtml() {
        return resourceLoader.getBytes("/index.html")
                .map(b -> Response.ok(b).type(MediaType.TEXT_HTML_TYPE).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/pom.png")
    public Response pomPng() {
        return resourceLoader.getBytes("/pom.png")
                .map(b -> Response.ok(b).type("image/png").build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
