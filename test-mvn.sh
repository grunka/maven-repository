#!/usr/bin/env bash

#rm -rf testrepo
mvn -Dmaven.repo.local=testrepo -DaltDeploymentRepository=test::http://localhost:9999/repository --settings maven-settings-test.xml $@
