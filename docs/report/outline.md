[//]: # (A Raft implementation using the actor model: Telegraft, a distributed chat application)

# Problem statement

## Scope (boundary)

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

[CockroachDB](https://www.cockroachlabs.com/) and [Apache Cassandra](https://cassandra.apache.org/_/index.html) are 2 popular databases that apply the techniques described above

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

- there shouldn't be room for interpretation, in fact where needed [Raft paper](https://raft.github.io/raft.pdf) is very meticulous, of course implementors 
add their own flavour, but the core of the protocol is clearly defined
- it tries to be easy to learn and to understand (and the paper tries to show it).

Telegraft in order to be replicated needs consensus, in fact it implements Raft.

## Purpose (technical and scientific expectations)

The purpose of this POC and what I am interested in is if the Raft protocol can be implemented using the 
[actor model](https://en.wikipedia.org/wiki/Actor_model): 

- if it is easier to implement it through actors than the typical object-oriented paradigm
- the performance of such implementation (which of course is not tied only to the actor model but also to the runtime chosen).

At first glance the actor model is a perfect fit for Raft: a node (or server) in a cluster is an actor,
such actor has the usual 3 states Follower, Leader, Candidate, it reacts differently, meaning replies and sends different
messages depending on the current state. The following paragraphs will confirm (or disprove) this observation.

<!-- The POC represents a typical way to deploy and use the Raft algorithm, which is a replicated database, in fact the commands a client can send to the database are none other than queries toward a database. -->

## Aspects to be investigated

 - explore how the actor model can be used to implement the Raft algorithm.
 - stress test
 - speed (how many successful actions in a fixed amount of time)
 - latency
 - observe behavior against different number of replicas.

Typical workload of a chat application: many writes (messages sent) and much more periodic small reads (messages received).

# Work product

## Technical choices made in the realization of the PoC

Telegraft comprises 3 separate projects:

 - `telegraft-benchmark-service`, which provides a benchmark to the application
 - `telegraft-statemachine-service`, which provides some gRPCs to interact with a database
 - `telegraft-raft-service`, which is the raft implementation.

All these 3 services are implemented using Scala 2.13 and communicate between each other using a gRPC protocol.
A REST API was considered as well, but gRPC is well suited for a microservices oriented architecture and, it is
surprisingly simple to implement.

### The state machine: `telegraft-statemachine-service`

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

### The Raft implementation: `telegraft-raft-service`

![The class diagram of the Raft implementation.](../diagrams/out/cd_TelegraftRaftService.svg)

#### Testing Raft

### The benchmark: `telegraft-benchmark-service`

### Deployment

### 



## Design of the evaluation experiments

machine specs

 - test with message queue
 - test without message queue

## Results of the evaluation experiments

Bottleneck/error in the implementation
Message queue


# Self-assessment

## Candidate’s own critique of own exam work (achievements, failures)

Couldn't show Raft correctness. Many important features both of Raft and both
of a normal production ready application are missing due to lack of time.

## Discussion of the candidate’s learning outcomes


This journey involved lots technologies, gRPC, databases, Akka, and a benchmark tool which will be valuable tools under the belt. Designing a distributed application is hard. Raft algorithm. Implementing protocols. Actor interaction patterns. Futures. Docker. 