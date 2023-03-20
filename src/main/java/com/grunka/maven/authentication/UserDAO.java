package com.grunka.maven.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDAO {
    private static final Logger LOG = LoggerFactory.getLogger(UserDAO.class);
    private static final String CREATE_TABLE_SQL = "create table users\n" +
            "(\n" +
            "    username TEXT not null,\n" +
            "    password TEXT not null,\n" +
            "    access   TEXT not null\n" +
            ");\n" +
            "\n" +
            "create unique index users_username_uindex\n" +
            "    on users (username);\n";
    private final Path userDatabaseLocation;
    private final PasswordValidator passwordValidator;

    public UserDAO(Path userDatabaseLocation, PasswordValidator passwordValidator) {
        this.userDatabaseLocation = userDatabaseLocation;
        this.passwordValidator = passwordValidator;
    }

    public Optional<User> validate(String username, String password) {
        boolean databaseExists = Files.exists(userDatabaseLocation);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + userDatabaseLocation)) {
            if (!databaseExists) {
                try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_TABLE_SQL)) {
                    preparedStatement.executeUpdate();
                }
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT password, access FROM users WHERE username = ?")) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        String passwordHash = resultSet.getString("password");
                        boolean shouldUpdateHash = passwordValidator.shouldUpdateHash(passwordHash);
                        Access access = Access.valueOf(resultSet.getString("access"));
                        if (passwordValidator.validate(passwordHash, password)) {
                            if (shouldUpdateHash) {
                                updateHash(username, password, connection);
                            }
                            return Optional.of(new User(username, access));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to access user database", e);
        }
        return Optional.empty();
    }

    private void updateHash(String username, String password, Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
            preparedStatement.setString(1, passwordValidator.createHash(password));
            preparedStatement.setString(2, username);
            preparedStatement.executeUpdate();
        }
    }
}
