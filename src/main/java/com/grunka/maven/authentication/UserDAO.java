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
import java.util.concurrent.locks.ReentrantLock;

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
    private final ReentrantLock databaseLock = new ReentrantLock(true);

    public UserDAO(Path userDatabaseLocation, PasswordValidator passwordValidator) {
        this.userDatabaseLocation = userDatabaseLocation;
        this.passwordValidator = passwordValidator;
    }

    public static void createDatabase(Path userDatabaseLocation) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + userDatabaseLocation)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_TABLE_SQL)) {
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<User> authenticate(String username, String password) {
        if (!Files.exists(userDatabaseLocation)) {
            return Optional.empty();
        }
        Optional<String> hash = getHash(username);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        if (passwordValidator.validate(password, hash.get())) {
            if (passwordValidator.shouldUpdateHash(hash.get())) {
                updateHash(username, passwordValidator.createHash(password));
            }
            return Optional.of(new User(username, getAccess(username).orElse(Access.none)));
        }
        return Optional.empty();
    }

    public boolean addUser(String username, String password, Access access) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO users (username, password, access) VALUES (?, ?, ?)")) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, passwordValidator.createHash(password));
                preparedStatement.setString(3, access.toString());
                return preparedStatement.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            LOG.error("Failed to add user {}", username, e);
            return false;
        } finally {
            databaseLock.unlock();
        }
    }

    private Optional<String> getHash(String username) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT password FROM users WHERE username = ?")) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(resultSet.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to read password hash for {}", username, e);
        } finally {
            databaseLock.unlock();
        }
        return Optional.empty();
    }

    private Optional<Access> getAccess(String username) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT accesss FROM users WHERE username = ?")) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(Access.valueOf(resultSet.getString(1)));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to read access for {}", username, e);
        } finally {
            databaseLock.unlock();
        }
        return Optional.empty();
    }

    private void updateHash(String username, String hash) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
                preparedStatement.setString(1, hash);
                preparedStatement.setString(2, username);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("Failed to update hash for username {}", username, e);
        } finally {
            databaseLock.unlock();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + userDatabaseLocation);
    }

    public boolean setAccess(String username, Access access) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE users SET access = ? WHERE username = ?")) {
                preparedStatement.setString(1, access.toString());
                preparedStatement.setString(2, username);
                return preparedStatement.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            LOG.error("Failed to update access to {} for user {}", access, username, e);
            return false;
        } finally {
            databaseLock.unlock();
        }
    }

    public boolean setPassword(String username, String password) {
        databaseLock.lock();
        try (Connection connection = getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
                preparedStatement.setString(1, passwordValidator.createHash(password));
                preparedStatement.setString(2, username);
                return preparedStatement.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            LOG.error("Failed to update password for user {}", username, e);
            return false;
        } finally {
            databaseLock.unlock();
        }
    }
}
