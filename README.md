# Telegraft

## How to improve this project

### Generation of IDs

Currently IDs of entities are generated via `java.util.UUID.randomUUID` which is "good enough"
in a small POC, but not in a production environment.

### Using streams

Both the telegraft-raft and the telegraft-state-machine modules could benefit from supporting
[streaming RPCs](https://grpc.io/docs/what-is-grpc/core-concepts/#bidirectional-streaming-rpc).
A streaming oriented gRPC service supports back-pressure.

### Data validation

For example: when a user is added to a chat, there is no check for the user existence.
Such checks could be added in the projection handlers. Persistent actors which represent
a piece of data that was not validated will be [passivated](https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#passivation)
automatically in order to reduce memory consumption.
