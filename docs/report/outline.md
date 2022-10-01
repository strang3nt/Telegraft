# Problem statement

## Scope (boundary)

Realisation of a small POC, a distributed (replicated) chat app. Such app won't support authentication nor session.
Any user is able to send and read messages from and to a chat.
The project uses technologies from Akka and Scala and Docker and Kubernetes for the deployment.

## Purpose (technical and scientific expectations)

 - implement Raft algorithm through Akka and actors
 - observe and compare the behavior of different consensus algorhtms (namely Raft and Gossip).

 I expect gossip to be faster, because it supports a nosql database, tuned for reads.
 On the opposite side CockroachDB supports PostgreSQL which is a relational database, whose main purpose is space complexity.
 CockroachDB was built with the main concern of being consistent across many widespread replicas.
 This difference is also what explain the different consensus algorithm.

## Aspects to be investigated

 - explore how the actor model can be used to implement the Raft algorithm.
 - cockroachDB vs Cassandra:
   - are all replicas really consistent at any time? (stress test)
   - speed (how many successfull actions in a fixed amount of time)
   - observe behavior against different number of replicas

Tipical workload of a chat application: many writes (messages sent) and much more periodic small reads (messages received).

# Work product

## Technical choices made in the realization of the PoC

## Design of the evaluation experiments

## Results of the evaluation experiments

# Self-assessment

## Candidate’s own critique of own exam work (achievements, failures)

## Discussion of the candidate’s learning outcomes
