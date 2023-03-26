package com.grunka.maven.authentication;

import java.util.Optional;

public interface UserAuthenticator {
    Optional<User> authenticate(String username, String password);
}
