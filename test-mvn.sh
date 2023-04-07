#!/usr/bin/env bash

#rm -rf testrepo
mvn -Dmaven.repo.local=testrepo -DaltDeploymentRepository=testing-deploy::http://localhost:8888/repository --settings maven-settings-test.xml $@
