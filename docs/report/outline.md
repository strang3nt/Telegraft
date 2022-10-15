# Problem statement

## Scope (boundary)

Realisation of a small POC, a distributed (replicated) chat app. Such app won't support authentication nor session.
Any user is able to send and read messages from and to a chat.
The app is distributed in the sense that there are multiple instances, able to serve clients simultaneously, with the goal
of having a low latency (response time). This poses the following problems: in order to support multiple instances of the
same application each instance needs to have access to the same data. Having a single database would be a bottleneck, two
are the solution applied:

 - either the database itself can be split, tables can be distributed or the tables themselves can be split or
 - the database is replicated, which is the solution explored in this report.

In order to replicate the data a consensus algorithm is needed, such as Raft.
The project uses technologies from Akka and Scala and Kubernetes for the deployment.

## Purpose (technical and scientific expectations)

 - Implement Raft algorithm through Akka and actors
 - observe the performance of the new implementation through a suite of benchmarks.

Benchmarks should exploit the latency of the algorithm under different conditions.

 

## Aspects to be investigated

 - explore how the actor model can be used to implement the Raft algorithm.
 - cockroachDB vs Cassandra:
   - are all replicas really consistent at any time? (stress test)
   - speed (how many successful actions in a fixed amount of time)
   - observe behavior against different number of replicas

Typical workload of a chat application: many writes (messages sent) and much more periodic small reads (messages received).

# Work product

## Technical choices made in the realization of the PoC

## Design of the evaluation experiments

## Results of the evaluation experiments

# Self-assessment

## Candidate’s own critique of own exam work (achievements, failures)

## Discussion of the candidate’s learning outcomes
