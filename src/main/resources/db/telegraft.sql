CREATE TABLE IF NOT EXISTS customer (
    id serial PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
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
    customer_id INTEGER NOT NULL,
    chat_id INTEGER NOT NULL,
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
