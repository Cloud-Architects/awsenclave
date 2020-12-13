#!/usr/bin/env bash

// To be run in EC2 host to prepare development environment


nitro-cli console --enclave-id i-097832b43d6f0ba34-enc1765c0aa14a79e1

cd awsenclave

nitro-cli terminate-enclave --all
git checkout . && git pull
./mvnw -f aws-enclave-example/aws-enclave-example-enclave/pom.xml compile package jib:dockerBuild
docker build deploy/enclave -t aws-enclave-example-enclave
nitro-cli build-enclave --docker-uri aws-enclave-example-enclave:latest --output-file sample.eif
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10 --debug-mode

./mvnw -f aws-enclave-example/aws-enclave-example-host/pom.xml compile exec:exec -Denclave.cid=10
nitro-cli describe-enclaves
nitro-cli terminate-enclave --all

