#!/bin/sh

# Assign an IP address to local loopback
sudo ifconfig lo 127.0.0.1

java -cp /app/resources:/app/classes:/app/libs/* solutions.cloudarchitects.awsenclave.example.enclave.ExampleEnclaveMain