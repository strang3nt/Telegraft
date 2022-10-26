# Telegraft 

## How to improve this module

### Generation of IDs

Currently IDs of entities are generated via `java.util.UUID.randomUUID` which is "good enough"
in a small POC, but not in a production environment.
