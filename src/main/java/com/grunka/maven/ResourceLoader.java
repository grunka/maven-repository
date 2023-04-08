package com.grunka.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(IndexResource.class);
    private final Map<String, Optional<byte[]>> lazyLoadCache = new ConcurrentHashMap<>();
    public Optional<byte[]> getBytes(String resource) {
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

}
