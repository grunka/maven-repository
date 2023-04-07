package com.grunka.maven.authentication;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Priority(Priorities.AUTHENTICATION - 1)
public class DefaultUserFilter implements ContainerRequestFilter {
    public static final String DEFAULT_USERNAME = "defaultUser";
    public static final String DEFAULT_PASSWORD = "defaultPassword";
    private static final String DEFAULT_AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString((DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD).getBytes(StandardCharsets.UTF_8));

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            final String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (header.toLowerCase().startsWith("basic")) {
                return;
            }
        }
        request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, DEFAULT_AUTHORIZATION);
    }
}
