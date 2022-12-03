# Telegraft-raft (Rafktor)

Raft proto from https://github.com/paralin/raft-grpc

## Implementation choices

A raft server in the cluster is a grpc server, which handles its requests and responses
via a persistent actor, more precisely an [event sourced actor](https://doc.akka.io/docs/akka/current/typed/persistence.html#introduction): 
such actor is able to persist its state in storage.
An event sourced actor is slightly different (and more complex) than a classic actor,
it does not only receive commands and responses but produces events as well:

 - the actor receives a command, runs some validation checks
 - each command may produce an Event, events change the state
 - the actor should then be able to recover its state by simply replaying all the events produced.

Producing events is particularly useful for the following reasons:
 
 - it allows to decouple reactions to commands from state changes 
 - events could be observed from an external source (e.g. to keep a log).

In the following few paragraphs I refer to the actor which serves the raft node grpc server as
raft server or actor or simply server.

### Timers

Each raft server implements a timer: such timer works both as the election timeout and
the idle time after which the leader sends heartbeats to every other raft server in the cluster.
The timer is a random wait between 150 and 300 ms, as per Raft paper suggestion.
Each time the timer is elapsed the actor sends to itself a command:

 - if the server is currently a follower or a candidate, the command starts an election,
 - if the server is a leader, the command triggers the heartbeat
 - the timer is then restarted to a random wait.

### Clients interaction

Clients can query each one of the replicas, and they will get a response:

 - the raft paper suggests the client should send requests only to the leader, if a client
   sends a request to a follower the follower should answer with the leader address
 - the implementation supports clients sending requests to a follower, the follower will then
   forward the request to the leader and wait for the leader's answer, finally it will hand out
   such answer to the client.
 - if the current state is `candidate` then the request is stashed, meaning it is sent in the
   bottom of the actor's message queue, hoping that the state will be different when the request
   is reached.

> **NOTE** that this implementation choice could lead to frequent timeout errors on the client side,
> for this reason the raft cluster could keep track of the client identity and assign an id to each request
> in order to not compute a request twice. This is suggested in §4 of the following [document](https://web.stanford.edu/~ouster/cgi-bin/papers/OngaroPhD.pdf)

### Timeouts

### Log

There is a Log class, which wraps a collection of tuples of:

 - the log item (payload or request)
 - the term the log item was added
 - the client waiting for the response to the payload.

## Further development and investigation

 - InstallSnapshotRPC
 - cluster membership changes
 - clients identity and session and request id
 - there is no indication against persisting more than the vote, the term and the log and the state itself.
