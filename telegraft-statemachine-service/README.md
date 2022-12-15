# telegraft-statemachine-service

This is a simple service based on [Slick](https://scala-slick.org/) and 
[Akka-gRPC](https://doc.akka.io/docs/akka-grpc/current/index.html). It acts as a frontend
for a Postgresql database.
It can receive 6 gRPCs, which simulate what a chat service could use:

- `CreateUser`
- `SendMessage`
- `CreateChat`
- `JoinChat`
- `GetMessages` 
- `GetChatUsers`.

The data model and the gRPCs are intentionally kept as simple as possible, as
the goal of this small service is only to provide a use case for the raft cluster.

## Data model

![Data model](../docs/diagrams/out/database.svg)
