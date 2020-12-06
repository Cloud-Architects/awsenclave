# awsenclave
Easy creation of AWS enclaves.

# How to work with it

The project requires [vsockj](https://app.circleci.com/pipelines/github/Cloud-Architects/vsockj) binary file to work, available only on Linux OS, which means building must be done thought Docker image if not working on Linux.

Sample queries:
```shell
docker run -v "$HOME/.m2":/var/maven/.m2 -ti --rm -u `id -u` -v "$PWD":/usr/src/mymaven \
-e MAVEN_CONFIG=/var/maven/.m2 -w /usr/src/mymaven \
maven-gcc mvn -Duser.home=/var/maven clean nar:nar-unpack package
```

```shell
docker run -v "$HOME/.m2":/var/maven/.m2 -ti --rm -u `id -u` -v "$PWD":/usr/src/mymaven \
-e MAVEN_CONFIG=/var/maven/.m2 -w /usr/src/mymaven \
maven-gcc mvn -Duser.home=/var/maven package
```

# Deployment

Sample deployment Docker images can be found in `deploy` directory.

## Security considerations

1. The project requires [vsockj](https://app.circleci.com/pipelines/github/Cloud-Architects/vsockj) binary file to work. To be able to review the resulting file, it's recommended to build the binary library, from [vsockj-native](https://github.com/Cloud-Architects/vsockj/tree/main/vsockj-native).
2. The AWS Enclaves use Docker to create an image used to run an enclave. That can be problematic on Windows machines developers can use Docker Engine to break Windows security.
