-- GameFlix schema (matches what Hibernate generates for the User entity)
-- Run once as a MySQL admin:
--   CREATE DATABASE gameflix CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--   CREATE USER 'gameflix_user'@'localhost' IDENTIFIED BY 'change-me';
--   GRANT ALL PRIVILEGES ON gameflix.* TO 'gameflix_user'@'localhost';

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL,
    password      VARCHAR(255) NOT NULL,   -- sized for the hash ({bcrypt}$2a$12$... = 67 chars), not the password
    display_name  VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
