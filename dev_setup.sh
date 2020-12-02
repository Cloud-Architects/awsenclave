#!/usr/bin/env bash

// To be run in EC2 host to prepare development environment

sudo amazon-linux-extras enable corretto8
sudo yum install java-1.8.0-amazon-corretto-devel gcc-c++ git python3 -y

sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo
sudo sed -i s/\$releasever/6/g /etc/yum.repos.d/epel-apache-maven.repo
sudo yum install -y apache-maven

sudo alternatives --config java
sudo alternatives --config javac

git clone https://github.com/Cloud-Architects/vsockj
cd vsockj
mvn install
mvn -f vsockj-example/pom.xml compile exec:exec

nano vsockj-example/src/main/java/solutions/cloudarchitects/vsockj/Demo.java
nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path ../sample.eif --enclave-cid 10
nitro-cli terminate-enclave --all
nitro-cli describe-enclaves