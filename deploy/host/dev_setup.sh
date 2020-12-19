#!/usr/bin/env bash

// To be run in EC2 host to prepare development environment


cd awsenclave

nitro-cli terminate-enclave --all
git checkout . && git pull
./mvnw install
./mvnw -f aws-enclave-example/aws-enclave-example-enclave/pom.xml compile package jib:dockerBuild
sed -i 's/ENCLAVE_REGION/ap-southeast-1/g' deploy/enclave-proxy/Dockerfile
docker build deploy/enclave-proxy -t aws-enclave-example-enclave
nitro-cli build-enclave --docker-uri aws-enclave-example-enclave:latest --output-file sample.eif
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10 --debug-mode
nitro-cli console --enclave-id $(nitro-cli describe-enclaves | jq -r ".[0].EnclaveID")

./mvnw -f aws-enclave-example/aws-enclave-example-host/pom.xml compile exec:exec -Denclave.cid=10 -Dencrypted.text=sth -Dkey.id=123
nitro-cli describe-enclaves
nitro-cli terminate-enclave --all

