# awsenclave
Easy creation of [AWS enclaves](https://docs.aws.amazon.com/enclaves/latest/user/nitro-enclave.html) in Java.

# How to work with it

The project requires [vsockj](https://app.circleci.com/pipelines/github/Cloud-Architects/vsockj) binary file to work, available only on Linux OS, which means building must be done thought Docker image if not working on Linux.

When developing and running locally, ensure latest jars are in your local mvn repo.

```shell
docker build -t maven-gcc - < Dockerfile-build
docker run -w /app -v "$HOME/.m2":/app/.m2 -v "$PWD":/app -ti --rm -u `id -u` \
maven-gcc ./mvnw -Dmaven.repo.local=/app/.m2/repository compile install
```

or
```shell
./mvnw compile install
```

## awsenclave-setup
The setup right now supports one case - Enclave decryption. An overview diagram is shown below:
![proxy overview](docs/awsenclave-proxy.png)

To run the example Host + enclave setup and verify communication, run the following command (ensure default credentials allow you to create new EC2 instances, create IAM roles and KMS key):
```shell
./mvnw -f awsenclave-setup/pom.xml compile exec:exec
```
The command should:
1. setup necessary resources (IAM role, KMS key, EC2 instance)

2. As a data owner, encrypt a text

3. As an administrator, run enclave,  KMS proxy and host application
   
4. Make host invoke a request to a server in enclave with the ciphertext
   
5. Make enclave decrypt the ciphertext and return decrypted plaintext.

6. Display the decrypted text and remove the test EC2 instance


Enclave server communicates with KMS through proxy. Communication enclave<->KMS uses HTTPS and is not accessible to host.

The example assumes no `kms:RecipientAttestation:ImageSha384` is passed nor verified by KMS.

For testing of the sample deployment, it's good to comment out instance termination in [SetupMain.java](https://github.com/Cloud-Architects/awsenclave/blob/main/awsenclave-setup/src/main/java/solutions/cloudarchitects/awsenclave/setup/SetupMain.java#L52) and building the code locally with commands from `deploy/host/dev_setup.sh`.

`awsenclave-setup` is intended only to perform a showcase of Nitro Enclaves and awsenclave and vsockj libraries. It's not in the scope of the project to provide infrastructure recommendations.

## awsenclave-example-enclave
To build (preferable run from host or other linux):
```shell
./mvnw -f awsenclave-example/awsenclave-example-enclave/pom.xml clean nar:nar-unpack package jib:dockerBuild
```

If not working on Linux:
```shell
docker run -w /app -v "$HOME/.m2":/app/.m2 -v "$PWD":/app -ti --rm -u `id -u` \
amazoncorretto:8u275 ./mvnw -Dmaven.repo.local=/app/.m2/repository -f awsenclave-example/awsenclave-example-enclave/pom.xml \
clean nar:nar-unpack package


./mvnw -f awsenclave-example/awsenclave-example-enclave/pom.xml compile  jib:dockerBuild
```

To test locally:
```shell
docker run awsenclave-example-enclave:latest
```
or
```shell
./mvnw -f awsenclave-example/awsenclave-example-enclave/pom.xml compile exec:exec
```

To show logs in a running enclave:
```shell
nitro-cli console --enclave-id [enclave-id]
```

## awsenclave-example-host
To test locally:
```shell
./mvnw -f awsenclave-example/awsenclave-example-host/pom.xml compile exec:exec -Denclave.cid=[CID] -Dencrypted.text=[base 64 encrypted text] -Dkey.id=[key id]
```

```shell
docker run -w /app -v "$HOME/.m2":/app/.m2 -v "$PWD":/app -ti --rm -u `id -u` \
amazoncorretto:8u275 ./mvnw -Dmaven.repo.local=/app/.m2/repository -f awsenclave-example/awsenclave-example-host/pom.xml \
compile exec:exec -Denclave.cid=23
```

# Deployment

Sample deployment Docker images can be found in `deploy` directory.

## Security considerations

1. The project requires [vsockj](https://app.circleci.com/pipelines/github/Cloud-Architects/vsockj) binary file to work. To be able to review the resulting file, it's recommended to build the binary library, from [vsockj-native](https://github.com/Cloud-Architects/vsockj/tree/main/vsockj-native).
2. The AWS Enclaves use Docker to create an image used to run an enclave. That can be problematic on Windows machines as developers can use Docker Engine to break Windows security.
