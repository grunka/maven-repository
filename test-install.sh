#!/usr/bin/env bash

rm -rf testrepo
mvn -Dmaven.repo.local=testrepo --settings maven-settings-test.xml install -U
