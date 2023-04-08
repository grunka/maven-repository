# maven-repository

[![maven-repository](https://github.com/grunka/maven-repository/actions/workflows/maven.yml/badge.svg)](https://github.com/grunka/maven-repository/actions/workflows/maven.yml)

## Summary

Have you ever installed and tried to maintain a maven repository and thought, this is way more than I need and it can't be that hard to build this kind of thing? I have many times and finally I went through with it and this is the result. Using the frameworks I'm most familiar with making a thing that does what I need.

Some of the decisions made here

- Very limited user capabilities. Just read and write levels.
- No advanced segmentations of artifact locations. Local and a number of remote repositories are checked in the order of configuration.
- Deploying is done to the local repository storage location both for snapshots and releases, no special routing to configure.
- Artifacts stored as files on disk instead of adding various database dependencies.
- Never tries to fetch SNAPSHOT version from remote repositories.

## Configuration

All configuration lives in the [yaml file](maven-repository.yml). There are comments for what the fields are used for. You will need to update it since by default no-one will be allowed to download any artifacts through the repository.

## Running

Starting the service 
```shell
java -jar maven-repository.jar server maven-repository.yml
```

Create a sqlite user database in the file `storage/users.sqlite`
```shell
java -jar maven-repository.jar create-database -f storage/users.sqlite
```

Add a user to the database in the file `storage/users.sqlite`, will be prompted for username, password, and access.
```shell
java -jar maven-repository.jar add-user -f storage/users.sqlite
```

Sets the password for a user in the database in the file `storage/users.sqlite`, will be prompted for username and password.
```shell
java -jar maven-repository.jar set-user-password -f storage/users.sqlite
```

Sets the access level for a user in the database in the file `storage/users.sqlite`, will be prompted for username and access.
```shell
java -jar maven-repository.jar set-user-access -f storage/users.sqlite
```

Generates a password hash using the method used in the sqlite database. Will be prompted for a password. Hash can also be used for the users in the yaml configuration file.
```shell
java -jar maven-repository.jar generate-password-hash
```

Generates a password hash using the classic mysql hash method, which is a double sha1 hash. Will be prompted for a password. Hash can also be used for the users in the yaml configuration file.
```shell
java -jar maven-repository.jar generate-mysql-password-hash
```
