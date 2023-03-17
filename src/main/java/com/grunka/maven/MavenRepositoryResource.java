package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/repository")
public class MavenRepositoryResource {
    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryResource.class);

    @HEAD
    @Path("/{path:.+}")
    public Response head(@Context UriInfo uriInfo, @PathParam("path") String path) {
        LOG.info("HEAD {}", uriInfo);
        return Response.ok().build();
    }

    @GET
    @Path("/{path:.+}")
    public Response get(@Context UriInfo uriInfo, @PathParam("path") String path) {
        LOG.info("GET {}", uriInfo);
        return Response.ok().build();
    }

    @POST
    @Path("/{path:.+}")
    public Response post(@Context UriInfo uriInfo, @PathParam("path") String path) {
        LOG.info("POST {}", uriInfo);
        return Response.ok().build();
    }
}
