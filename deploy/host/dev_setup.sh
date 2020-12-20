#!/usr/bin/env bash

// To be run in EC2 host to prepare development environment


cd awsenclave

nitro-cli terminate-enclave --all
git checkout . && git pull
./mvnw install
./mvnw -f awsenclave-example/awsenclave-example-enclave/pom.xml compile package jib:dockerBuild
sed -i 's/ENCLAVE_REGION/ap-southeast-1/g' deploy/enclave-proxy/Dockerfile
docker build deploy/enclave-proxy -t awsenclave-example-enclave
nitro-cli build-enclave --docker-uri awsenclave-example-enclave:latest --output-file sample.eif
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10 --debug-mode
nitro-cli console --enclave-id $(nitro-cli describe-enclaves | jq -r ".[0].EnclaveID")

./mvnw -f awsenclave-example/awsenclave-example-host/pom.xml compile exec:exec -Denclave.cid=10 -Dencrypted.text=sth -Dkey.id=123

./mvnw -f awsenclave-example/awsenclave-example-host/pom.xml compile exec:exec -Denclave.cid=10 -Dencrypted.text=AQICAHiXE3+or4JmNf8uRRVw2DwRQmTYsszELIZwBo/PzBDxYQFNCGTXv/gdtGxcXUqMRzTtAAAA3TCB2gYJKoZIhvcNAQcGoIHMMIHJAgEAMIHDBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDEIAmewlhDpwsCzp0wIBEICBlXPPi6YWqoaao5HJeI2TU9PfyFwNXEij2cORjnns9YouOBePwSbknreTkmQhIGmDZiR36Ef54IJP47vXNzr+TT4vrVlPay9JjXTWyyDHdwyrBPAe9U3Fd0IkXqZd10Bb4VO5ZtL2VJvdx42uBxQ4BRrgFDS+FPT3iBlIadJseERUtX5lDxNlbyHC5ET/Ag5WoxlwOY/n -Dkey.id=510e6380-0a86-48a9-94df-d9cc4b2c55ef

nitro-cli describe-enclaves
nitro-cli terminate-enclave --all

