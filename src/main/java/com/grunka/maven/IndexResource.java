package com.grunka.maven;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

@Path("/")
public class IndexResource {
    @GET
    @Path("/")
    public Response indexHtml() {
        try (InputStream resourceAsStream = getClass().getResourceAsStream("/index.html")) {
            byte[] pomBytes = resourceAsStream.readAllBytes();
            return Response
                    .ok(pomBytes)
                    .type(MediaType.TEXT_HTML_TYPE)
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/pom.png")
    public Response pomPng() {
        try (InputStream resourceAsStream = getClass().getResourceAsStream("/pom.png")) {
            byte[] pomBytes = resourceAsStream.readAllBytes();
            return Response
                    .ok(pomBytes)
                    .type("img/png")
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
