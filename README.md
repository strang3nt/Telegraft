# Telegraft

Telegraft is a university project whose goal is study the [Raft consensus algorithm](https://raft.github.io/):
it is very simple and stripped down distributed and replicated chat application with Raft built in.
Telegraft uses Scala and relies heavily on Akka technologies.

## Architecture

Since it is a distributed application there are many Telegraft nodes, each one is composed with 3 services:

 - `telegraft-rest-service` which acts as a facade to the other services, simply receives a http request and routes it to a module
 - `telegraft-raft-service` which is the Raft implementation
 - `telegraft-statemachine-service` which is where the actual business logic happens, the state machine receives read 
    and write requests from (respectively) the rest and raft services and queries a database in order to answer accordingly.

## How to improve this project

### Session and authentication

These are arguably the most important missing features. Both **were not even investigated** for 
lack of time.

### Generation of IDs

Currently IDs in the database are generated via `java.util.UUID.randomUUID` which is "good enough"
in a small POC, it also works because the database itself is only replicated, not sharded and 
the Raft protocol allows only the leader of the cluster to write data (there are no concurrent writes).

Of course this is not good in a production environment, where usually data is sharded and there are many
concurrent writes, many clusters each of them using some kind of consensus protocol. A solution was provided
by Twitter with [Snowflake](https://blog.twitter.com/engineering/en_us/a/2010/announcing-snowflake), the project
is now archived but there are many updated forks.

### Using streams

Both the telegraft-raft and the `telegraft-statemachine-service` modules could benefit from supporting
[streaming RPCs](https://grpc.io/docs/what-is-grpc/core-concepts/#bidirectional-streaming-rpc).
A streaming oriented gRPC service supports back-pressure.

### Data validation

For example: when a user is added to a chat, there is no check for the user existence.
Such checks could be added in the projection handlers. Persistent actors which represent
a piece of data that was not validated will be [passivated](https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#passivation)
automatically in order to reduce memory consumption.
