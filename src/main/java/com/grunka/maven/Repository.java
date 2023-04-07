package com.grunka.maven;

import java.net.URI;

public record Repository(URI url, String username, String password) {
}
