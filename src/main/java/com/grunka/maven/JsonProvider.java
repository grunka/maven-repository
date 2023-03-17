package com.grunka.maven;

import com.grunka.json.Json;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JsonProvider<T> implements MessageBodyReader<T>, MessageBodyWriter<T> {
    @Context
    private UriInfo ui;

    public JsonProvider() {
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType,
                              Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public T readFrom(Class<T> type, Type genericType, Annotation[] annotations,
                      MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
                      InputStream entityStream) throws IOException, WebApplicationException {

        String input = new String(entityStream.readAllBytes(), StandardCharsets.UTF_8);
        return Json.objectify(input, type);
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType,
                               Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public long getSize(T t, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws WebApplicationException {
        try (PrintWriter printWriter = new PrintWriter(entityStream)) {
            String json = Json.stringify(t);
            printWriter.write(json);
            printWriter.flush();
        }
    }
}
