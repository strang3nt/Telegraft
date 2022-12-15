# telegraft-benchmark-service

This project is a benchmark and a stress test for a raft cluster (of `telegraft-raft-service`s).
Sends network packets and waits for a successful response.
Network packets are sent towards `localhost:8350`, `localhost:8351` and `localhost:8352`.
A docker-compose configuration which starts all the necessary services and populates the database can be found
in this folder.

## The benchmark

The prerequisites are that:

 - there are local docker images of both `telegraft-raft-service` and `telegraft-statemachine-service`
 - the database is preloaded with the data provided in `/src/main/resources/db` (which is satisfied as long as the 
   benchmark is run against the provided docker-compose configuration).

The benchmark comprises (roughly) the following steps:

1. in parallel, an increasing number of users, until 100, during the span of 20 seconds, going on until all the 
responses are received or 100 failed requests are received
2. send a gRPC `ClientQuery` request to one of the 3 addresses, such gRPC contains a request `GetMessages` for a 
random user, for the state machine
3. send a gRPC `ClientRequest` request to the same address as before, with a `SendMessage` payload, which sends a 
message to a random chat of the previous user
4. if any of these gRPCs responses have `status = false`, then the response is counted as a failure.

## Results

[//]: # (TODO)
