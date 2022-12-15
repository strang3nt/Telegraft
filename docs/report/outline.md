[//]: # (Telegraft, distributed chat application)

# Problem statement

## Scope (boundary)

The goal of this report is to present a POC and a Raft consensus algorithm implementation, measure the performance of 
such implementation and draw some conclusions about the experience.

Telegraft is persistent, replicated and distributed:

- persistent because Telegraft persist in stable storage its events
- distributed in the sense that there are multiple instances of Telegraft operating at the same time
- replicated because each instance of Telegraft always operates on the same set of persisted data.

When dealing with a persistent and distributed dataset, the following 3 ideas are the ones actually applied:

- either the database itself can be split, tables can be distributed or the tables themselves can be split or
- the database is replicated, which is the solution explored in this report
- a hybrid solution in which the database itself is split between the servers and replicated as well.

In order to support multiple instances of the same application and to have such application behave the same in every
replica there must be some way to keep data synchronized correctly. This problem is called consensus: each replica
should agree on the data and should have an updated version of such data at any moment. 
Raft algorithm is implemented in Telegraft in order to have the consensus.

## Purpose (technical and scientific expectations)

 - Implement Raft algorithm through Akka and actors pattern

 - observe the performance of the new implementation through a suite of benchmarks.

Benchmarks should exploit the latency of the algorithm under different conditions.

## Aspects to be investigated

 - explore how the actor model can be used to implement the Raft algorithm.
 - are all replicas really consistent at any time? (stress test)
 - speed (how many successful actions in a fixed amount of time)
 - latency
 - observe behavior against different number of replicas.

Typical workload of a chat application: many writes (messages sent) and much more periodic small reads (messages received).

# Work product

## Technical choices made in the realization of the PoC

## Design of the evaluation experiments

## Results of the evaluation experiments

# Self-assessment

## Candidate’s own critique of own exam work (achievements, failures)

## Discussion of the candidate’s learning outcomes
