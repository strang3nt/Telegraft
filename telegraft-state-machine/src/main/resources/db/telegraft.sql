--DROP TABLE IF EXISTS public.event_journal;

CREATE TABLE IF NOT EXISTS event_journal(
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,

  writer VARCHAR(255) NOT NULL,
  write_timestamp BIGINT,
  adapter_manifest VARCHAR(255),

  event_ser_id INTEGER NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering);

--DROP TABLE IF EXISTS public.event_tag;

CREATE TABLE IF NOT EXISTS
    event_tag(
    event_id BIGINT,
    tag VARCHAR(256),
    PRIMARY KEY(event_id, tag),
    CONSTRAINT fk_event_journal
      FOREIGN KEY(event_id)
      REFERENCES event_journal(ordering)
      ON DELETE CASCADE
);

--DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,

  snapshot_ser_id INTEGER NOT NULL,
  snapshot_ser_manifest VARCHAR(255) NOT NULL,
  snapshot_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

--drop table if exists public.akka_projection_offset_store;

CREATE TABLE IF NOT EXISTS akka_projection_offset_store (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(4) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );

CREATE INDEX IF NOT EXISTS projection_name_index ON akka_projection_offset_store (projection_name);

--drop table if exists public.akka_projection_management;

CREATE TABLE IF NOT EXISTS akka_projection_management (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  paused BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY(projection_name, projection_key)
);

CREATE TABLE IF NOT EXISTS customer (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS chat (
    id VARCHAR(255) PRIMARY KEY,
    chatname VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    chat_id VARCHAR(255) NOT NULL,
    sent_time TIMESTAMP NOT NULL,
    content VARCHAR(255) NOT NULL,
    CONSTRAINT customer_id_fk
        FOREIGN KEY(customer_id)
        REFERENCES customer(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT chat_id_fk
        FOREIGN KEY(chat_id)
        REFERENCES chat(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_chat (
    customer_id VARCHAR(255) NOT NULL,
    chat_id VARCHAR(255) NOT NULL,
    CONSTRAINT customer_id_chat_id PRIMARY KEY(customer_id, chat_id),
    CONSTRAINT customer_id_fk
    FOREIGN KEY(customer_id)
    REFERENCES customer(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
    CONSTRAINT chat_id_fk
    FOREIGN KEY(chat_id)
    REFERENCES chat(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);
