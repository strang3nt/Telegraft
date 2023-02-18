# telegraft-statemachine-service

This is a simple service based on [Slick](https://scala-slick.org/) and 
[Akka-gRPC](https://doc.akka.io/docs/akka-grpc/current/index.html). It acts as a frontend
for a Postgresql database.
It can handle 6 gRPCs, which simulate what a chat service could use:

- `CreateUser`
- `SendMessage`
- `CreateChat`
- `JoinChat`
- `GetMessages` 
- `GetChatUsers`.

## Data model

![Data model](../docs/diagrams/out/database.svg)

## How to run

These are the instructions to build and run the project in a docker container.
The project must be compiled first, using `sbt docker:publishLocal` this command
builds the project and creates an Alpine container inside the local docker
environment.

It is provided a docker-compose configuration, which starts this service and
a postgres database, the 2 services can be found locally at ports `8301` and `5432`.
After starting the configuration one can try to launch a gRPC command, for example using
`grpcurl` or a client such as BloomRPC. For example:

```
grpcurl -d '{"username":"Alessandro"}' -plaintext 127.0.0.1:8301 com.telegraft.statemachine.CreateUser
```
