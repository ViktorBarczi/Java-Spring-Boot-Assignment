-- Executed once by the PostgreSQL image, when its data volume is empty.
-- Hibernate uses pooled sequences with an allocation size of 50.
CREATE SEQUENCE book_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE book_copy_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE book (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL UNIQUE,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(255) NOT NULL UNIQUE,
    published_year INTEGER NOT NULL
);

CREATE TABLE book_copy (
    id BIGINT PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id),
    is_available BOOLEAN NOT NULL
);

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);
