---
title: 'A Raft implementation using the actor model: Telegraft, a distributed chat application'
abstract: |
  This report contains the experience of developing a small POC, it is an implementation of
  the Raft protocol, using the actor model.
  The primary goal of the experience was to learn the Raft protocol and to understand 
  the challenges of building a distributed system. I also try to show the feasibility of
  implementing the protocol using the actor model within Akka's typed actors.
author:
- Alessandro Flori

institute: 
- University of Padua
- Department of Mathematics, Tullio Levi Civita
...

# Introduction

The goal of this report is to show a POC with a Raft consensus algorithm implementation, measure the performance of 
such implementation and draw some conclusions about the results and experience.
The project provides some gRPCs which simulates what I thought the APIs of a chat app could be,
the whole project was ironically named Telegraft.
Telegraft is a persistent, replicated and distributed application:

- persistent because Telegraft persist in stable storage its events
- distributed in the sense that there are multiple instances of Telegraft operating at the same time
- replicated because each instance of Telegraft always operates on the same set of persisted data.

Following an initial investigation I found out that when dealing with a persistent and distributed dataset, 
in an environment where high scalability is needed the following 3 ideas seem the ones applied in a real-world setting:

- either the database itself can be split, meaning the tables can be distributed across multiple servers 
  or the tables themselves can be split
- the database is replicated, which is the solution explored in this report
- a hybrid solution in which the database itself is somehow split between the servers and replicated as well.

[CockroachDB](https://www.cockroachlabs.com/) and [Apache Cassandra](https://cassandra.apache.org/_/index.html) are 2 
popular databases that apply the techniques described above.

In order to support multiple instances of the same application and to have always and transparently the same state 
in every replica there must be some way to keep data synchronized correctly. 
This problem is called consensus: each replica should agree on the data and should have the latest version of such 
data at any moment. 
The first algorithm that (arguably) solved this problem is Paxos, which has the following problems:

- it is a protocol, as such it is very loosely specified, and it must not be tied to any programming language nor 
implementations, the consequence is that there are many projects bringing a Paxos-like algorithm, but each one of them 
interprets Paxos in a somewhat personal way
- Paxos is a very complicated protocol, difficult to implement and to understand.

Paxos is followed more than 20 years later by Raft, which aims at solving the problems listed above:

- there shouldn't be room for interpretation, in fact where needed [Raft paper](https://raft.github.io/raft.pdf) is 
- very meticulous, of course implementors add their own flavour, but the core of the protocol is clearly defined
- it tries to be easy to learn and to understand (and the paper tries to show it).

Telegraft in order to be replicated needs consensus, in fact it implements Raft.

[//]: # (purpose and aspects to be investigated)

The purpose of this POC and what I am interested in is if the Raft protocol can be implemented using the 
[actor model](https://en.wikipedia.org/wiki/Actor_model): 

- if it is easier to implement it through actors than the typical object-oriented paradigm
- the performance of such implementation (which of course is not tied only to the actor model but also to the runtime chosen).

At first glance the actor model is a perfect fit for Raft: a node (or server) in a cluster is an actor,
such actor has the usual 3 states Follower, Leader, Candidate, it reacts differently, meaning replies and sends different
messages depending on the current state. The following paragraphs will confirm (or disprove) this observation.

I also want to test my implementation against a workload in order to show its performances.
For this purpose the POC contains a benchmark, such benchmark shows the latency and how it changes as the workload
grows.

# Work product

Telegraft comprises 3 separate projects:

 - `telegraft-benchmark-service`, which provides a benchmark to the application
 - `telegraft-statemachine-service`, which provides some gRPCs to interact with a database
 - `telegraft-raft-service`, which is the raft implementation.

All these 3 services are implemented using Scala 2.13 and communicate between each other using a gRPC protocol.
A REST API was considered as well, but gRPC is well suited for a microservices oriented architecture and, it is
surprisingly simple to implement.

![Communication between services](../diagrams/out/cd_sendGetMessage.svg)

## The state machine: `telegraft-statemachine-service`

As the title and the name of the service suggests, the service is the state machine mentioned in the Raft paper.
It provides a state, which is the database, as well as something to test the Raft implementation on.

The service uses the following technologies:

 - gRPC and `Akka-gRPC` that provides the necessary APIs to work with gRPC in Scala
 - Slick, which is a Scala library for database access and manipulation
 - it is plugged in to a Postgresql instance.

It is a very simple service overall, it provides some gRPCs which I think could be used in a chat application,
receives requests and executes them against a database. Such requests are:

 - `CreateUser(username)`, creates a user with the given username
 - `CreateChat(userId, chatName)`, creates a chat with a member and the given chat name
 - `JoinChat(userId, chatId)`, a user joins a chat
 - `SendMessage(userId, chatId, content, sentTime)`, a user sends a message to the given chat with the given content,
and a timestamp which marks the time the message was sent
 - `GetMessages(userId, messagesAfter)`, gets all messages a user received in the chat they are a member of, after 
`messagesAfter` which is a timestamp
 - `getChatUsers(chatId)` gets all users of a chat.

![Telegraft database model](../diagrams/out/database.svg)

The database model is purposely very simple: a user, which was called `customer` can create and become member of one or 
more `chat`s, and can write one or more `message`s. 
A `chat` must have at least one member and contains zero or more `message`s. Each message is written by one
`customer` and it owned by one `chat`.

As I already said, the model is very simple: there are no validity checks, both in the model nor in the application 
itself, for example a `customer` can write in a `chat` he is not a member of.

Another simplification one can notice at first glance is that ids are autogenerated integers, which is not at all what 
is usually done in a real application. Take the table messages: in big chat applications millions, possibly billions 
of messages are sent per day, integer keys would finish very fast. A UUID could have been a smarter idea, but then I 
would need the primary key shipped directly with the command, otherwise in a distributed setting where there are many
`telegraft-statemachine-service`s the same record would have different primary keys across replicas. The latter means 
that there should be some middleware before `telegraft-statemachine-service` capturing a client's command, giving it a 
key before handling it to the state machine. Such middleware must generate consistent keys across all replicas. 
I decided not to deal with this complication.

## The Raft implementation: `telegraft-raft-service`

### Brief Akka typed actors

This subsection contains a brief introduction to Akka typed actors, it should be enough to follow the rest of the 
report.

The actor model
: The actor model is a way of designing concurrent processes and systems where an actor is the most basic entity.
An actor sends and receives messages and creates child actors and holds a local state. 
Actors interact between each other only through messages, follows that an actor can modify another actor's state only
by sending it a message.

Akka typed actors
: Akka typed actors are comprised of a behavior, a state and a mailbox. A behavior decides how an actor responds to 
a message (or command, as per Akka naming conventions). The mailbox is a queue, each new command sent to an actor is 
put here, waiting for its turn to be processed, the queue is FIFO. Actors in Akka are typed, meaning that they can only
receive a certain type of commands. An actor in Akka is a reference to an object that contains a state and 1 or more 
behaviors, that is because an actor's behavior can change as a consequence of receiving a certain command. Further
information here: <https://doc.akka.io/docs/akka/current/general/actors.html>

Akka event sourced behavior or persistent actor
: An event sourced behavior (or persistent actor) is a special type of actor: it is a persistent actor, meaning it can 
persist data in storage, and it generates events when it receives commands. Commands can no longer change the current 
state, but commands produce events, and events change the state. Events are what is actually persisted, and
replaying the events the order they arrived to the latest event will yield an actor's current state. Producing events
is useful for the following reasons: it allows to decouple reactions to commands from state changes, and events could
be fed into (or captured by) an external resource, for example to create a log or build a database.
Further information can be found at the following link: 
<https://doc.akka.io/docs/akka/current/typed/persistence.html#introduction>.

### Design

A raft server in the cluster is a grpc server, which handles its requests and responses
via a persistent actor.

![The class diagram of the Raft implementation.](../diagrams/out/cd_TelegraftRaftService.svg)

`telegraft-raft-service` is built around the component RaftServer: it is the Raft persistent actor, and it is
represented as a singleton object in the class diagram, this singleton object has an `apply` method which will create
an instance of a proper (persistent) actor. Both commands and events the actor receives and produces are defined
inside the object RaftServer itself, as per Akka convention. The state of the actor should be defined in the same
RaftServer object, but, since the line count was getting to large an object RaftState was created. RaftState contains
the states of a Raft actor (leader, follower, candidate) as well as how to react to events.

A RaftServer holds an instance of a Configuration, which holds a collection of `Server`s, which are the other 
Raft servers in the cluster. The Configuration reads a configuration file and then builds all the servers. A `Server`
holds information about how to reach a Raft server: sends and receives gRPC requests from a node.

There is a Log class (it is the Raft Log), which is collection of tuples comprised of:

- the log item (payload or request)
- the term the log item was added
- the client id and request id in order to retrieve the answer in the eventuality that the client did not receive the
  response 
- the (optional) response the raft server received from applying the payload to the state machine.

> Note that the grpc protocol itself does not carry the response of the raft server, meaning that the response is not
> sent along the entries passed in the AppendEntries RPC. That is because (of course) each
> raft server must apply the request to its own state machine.

The `telegraft-raft-service` project contains also the implementations of the gRPC protocols. Each Raft server has 
3 protocols:

 - a protocol to communicate between nodes in the Raft cluster
 - a protocol for clients to communicate with a Raft server
 - a protocol which defines how to communicate with the state machine.

The code that handles gRPC requests and responses is autogenerated, except for a server's request handling, for
example the first 2 protocols in the above list are implemented and the implementation is very simple:
when a payload is received it is properly wrapped and sent to the Raft persistent actor, which sends back a response,
such response is then mapped as a gRPC response and sent to whoever sent the payload.
Both the autogenerated code and the implementations are not represented in the class diagram.

### Raft algorithm and implementation choices

`telegraft-raft-service` implements the basic Raft algorithm, it only supports AppendEntries (g)RPCs and 
RequestVote (g)RPCs. InstallSnapshot RPCs and cluster membership changes could be a subject for future improvements.
Follows an image that represents the possible interactions between a client and a cluster of 3 Raft servers.

![Raft gRPC interactions.](../diagrams/out/raftClientProto.svg)

#### Raft (g)RPCs

In order to make any of the Raft RPCs (AppendEntries or RequestVote), I followed the same pattern: it is sent a request to each of the other
servers in the cluster, the result of this request is a Scala Future object, which at a certain 
point in time will contain the actual response. Akka provides APIs for interoperability between actors and futures, 
in particular there is a method called `pipeToSelf`, whenever a future is completed, the value obtained can be sent as
a message to the actor itself. More information at the following link: 
<https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#send-future-result-to-self>.

Figure as;ldkfjasdl; shows an example of usage of the `pipeToSelf` method: the persistent actor in messages #7 and #10
sends to self a message representing the result of the Future computation.

#### Interaction with clients

Clients can query each one of the replicas, and they will get a response:

- the raft paper suggests the client should send requests only to the leader, if a client
  sends a request to a follower the follower should answer with the leader's address
- the implementation supports clients sending requests to a follower, the follower will then
  forward the request to the leader and wait for the leader's answer, finally it will deliver
  such answer to the client.
- if the current state is `candidate` then the request is sent in the bottom of the actor's message queue,
  hoping that the state will be different when the queue reaches the request again.

My implementation borrows ideas from [Ongaro's Raft dissertation](https://web.stanford.edu/~ouster/cgi-bin/papers/OngaroPhD.pdf), chapter 4.
A diagram which shows a possible interaction is shown in figure: asdfas.
There are both the `ClientRequest` and the `ClientQuery` which should be used respectively when the client wants to 
make a write request and a read request. The `ClientQuery` gRPC is not linearized, because it queries immediately a 
state machine, a `ClientRequest` instead is linearizable and if wanted it is possible to send a read request as a 
`ClientRequest`. In Telegraft a read request is (for example) the request of getting new messages: I could either 
send my query as a `ClientRequest` so that I am sure I will receive the latest messages, or a `ClientQuery` if I want 
the lowest latency possible, since `ClientQuery` payload is sent right away to the state machine.

![State diagram of telegraft-raft-service client handling.](../diagrams/out/sd_clientRequest.svg)

Figure asdfasdf shows the behavior of the system when it receives a `ClientRequest`:

- whenever the entity `raftServer` receives a `ClientRequest` (message #3) the actor immediately sends to himself an
`ApplyToStateMachine` message, this message contains the necessary data in order to apply the command to the state 
machine and then reply to the client
- `raftServer` then sends to all other Raft servers in the cluster an `AppendEntriesRequest` and waits for the
responses, which when received will be put in the queue when ready
- if the `commitIndex` value has reached the payload index inside the Log, the command will be applied to the state
machine
- else if `raftServer` reaches the `ApplyToStateMachine` message before the payload should be applied, the message is
simply sent again to self, into the bottom of the message queue, if for example there have been a leadership change the
content of the `ApplyToStateMachine` message could be considered not consistent with the current log, and the message
can be discarded entirely.

#### Response timeouts and delays

In a network of nodes (raft servers) there can be many delays, such delays must be accounted and the Raft algorithm must
not fail if a timeout happens while waiting for a response. Waiting indefinitely for a response is not reasonable in
real world conditions. For this reason:

- when doing any network request a timeout is in place (a very generous 3 seconds timeout)
- if such timeout elapses instead of throwing an exception a "smart" answer is given, in such a way that the raft
  algorithm can continue, the answer is harmless, meaning that there will be no problems when the real answer is
  received.

#### The message queue

In Akka each actor has its own message queue, the default is an unbounded queue. One might think that a queue could 
lead to weird behaviors in the cluster, for example when time is involved (heartbeat and election timeouts), but it does
not, because every command is sent in the back of the queue. If a leader sends a heartbeat to the followers, but the
followers do not compute the heartbeat before the election timeout, every follower sends to itself a message, but,
since such message was sent after the heartbeat, the latter will reach the front of the queue first and the election 
timeout will be ignored.

An interesting aspect that could be investigated is: what if the concept of priority is introduced in the message queue? 
I could for example give the highest priority to the Raft messages such as AppendEntries and RequestVote requests and 
responses, or I could give priority to applying client's requests to the state machine. How does any of these changes
affect the latency? This is left for future investigations.

### Correctness of Raft implementation

I created some unit tests, which test the behavior of 1 Raft persistent actor against other mocked components.
These unit tests are not exhaustive and, while they are not static tests, meaning, the Raft actor is a real, working
instance they do not show the correctness of the implementation or prove any properties. In fact many errors and bugs
were solved while studying the behavior at runtime. A proper and fuller suite of integration and system tests, could
definitely be the subject of further development.

## Deployment

![Telegraft deployment communication diagram.](../diagrams/out/cd_sendGetMessage.svg)

## Evaluation experiments

### The benchmark: `telegraft-benchmark-service`

This project is a small Scala script built with a test and benchmarking framework called [Gatling](https://gatling.io/).
Gatling is originally a load testing tool for http protocols, it provides many interesting features, such as:

 - light simulation of multiple users (in fact it uses Akka and actors under the hood)
 - control over users, for example I can tell Gatling to slowly rump up the number of concurrent user
 - automatic report generation.

It acts both as a benchmark for a cluster of Raft nodes.
Inside the project's folder there is a docker-compose configuration, which loads 3 Raft servers, coupled with the
respective state machines. The state machines databases are preloaded with data.

The benchmark comprises (roughly) the following steps:

1. in parallel, an increasing number of users, until 100, during the span of 20 seconds, stay active until all the
   responses are received or 100 failed requests are received
2. send a gRPC `ClientQuery` request to one of the 3 Raft servers, such gRPC contains a request `GetMessages` for a
   random user, for the state machine
3. send a gRPC `ClientRequest` request to the same address as before, with a `SendMessage` payload, which sends a
   message to a random chat of the previous user
4. if any of these gRPCs responses have `status = false`, then the response is counted as a failure.


machine specs

 - test with message queue
 - test without message queue

### Results of the evaluation experiments

Bottleneck/error in the implementation
Message queue


# Self-assessment

## Candidate’s own critique of own exam work (achievements, failures)

Couldn't show Raft correctness. Many important features both of Raft and both
of a normal production ready application are missing due to lack of time.

## Discussion of the candidate’s learning outcomes


This journey involved lots technologies, gRPC, databases, Akka, and a benchmark tool which will be valuable tools under the belt. Designing a distributed application is hard. Raft algorithm. Implementing protocols. Actor interaction patterns. Futures. Docker. 