# Telegraft

## How to improve this project

### Generation of IDs

Currently IDs of entities are generated via `java.util.UUID.randomUUID` which is "good enough"
in a small POC, but not in a production environment.

### Using streams

Both the telegraft-raft and the telegraft-state-machine modules could benefit from supporting
[streaming RPCs](https://grpc.io/docs/what-is-grpc/core-concepts/#bidirectional-streaming-rpc).
A streaming oriented gRPC service supports back-pressure.
