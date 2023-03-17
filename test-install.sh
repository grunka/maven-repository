#!/usr/bin/env bash

mvn dependency:purge-local-repository
mvn --settings maven-settings-test.xml install -U
mvn dependency:purge-local-repository
