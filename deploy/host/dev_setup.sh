#!/usr/bin/env bash

// To be run in EC2 host to prepare development environment


cd awsenclave

nitro-cli terminate-enclave --all
git checkout . && git pull
./mvnw -f aws-enclave-example/aws-enclave-example-enclave/pom.xml compile package jib:dockerBuild
docker build deploy/enclave -t aws-enclave-example-enclave
nitro-cli build-enclave --docker-uri aws-enclave-example-enclave:latest --output-file sample.eif
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10 --debug-mode
nitro-cli console --enclave-id $(nitro-cli describe-enclaves | jq -r ".[0].EnclaveID")

./mvnw -f aws-enclave-example/aws-enclave-example-host/pom.xml compile exec:exec -Denclave.cid=10
nitro-cli describe-enclaves
nitro-cli terminate-enclave --all

