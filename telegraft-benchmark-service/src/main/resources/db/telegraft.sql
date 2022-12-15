CREATE TABLE IF NOT EXISTS customer (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS chat (
    id SERIAL PRIMARY KEY,
    chatname VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    chat_id INTEGER NOT NULL,
    sent_time TIMESTAMP NOT NULL,
    content VARCHAR(255) NOT NULL
);

ALTER TABLE message
ADD CONSTRAINT chat_id_fk
FOREIGN KEY(chat_id)
REFERENCES chat (id)
ON DELETE CASCADE
ON UPDATE CASCADE;

ALTER TABLE message
ADD CONSTRAINT customer_id_fk
FOREIGN KEY(customer_id)
REFERENCES customer (id)
ON DELETE CASCADE
ON UPDATE CASCADE;

CREATE TABLE IF NOT EXISTS customer_chat (
    customer_id INTEGER NOT NULL,
    chat_id INTEGER NOT NULL,
    CONSTRAINT customer_id_chat_id PRIMARY KEY (customer_id,chat_id)
);

ALTER TABLE customer_chat
ADD CONSTRAINT customer_id_fk
FOREIGN KEY(customer_id)
REFERENCES customer (id)
ON DELETE CASCADE
ON UPDATE CASCADE;

ALTER TABLE customer_chat
ADD CONSTRAINT chat_id_fk
FOREIGN KEY(chat_id)
REFERENCES chat (id)
ON DELETE CASCADE
ON UPDATE CASCADE;

COPY customer(id, username)
FROM '/docker-entrypoint-initdb.d/customers.csv'
DELIMITER ','
CSV HEADER;

COPY chat(id, chatname)
FROM '/docker-entrypoint-initdb.d/chats.csv'
DELIMITER ','
CSV HEADER;

COPY customer_chat(customer_id, chat_id)
FROM '/docker-entrypoint-initdb.d/customers_chats.csv'
DELIMITER ','
CSV HEADER;
