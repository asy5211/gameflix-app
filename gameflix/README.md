# GameFlix – Login & Registration Service (Module 4)

Java 21 (Eclipse Temurin) · Spring Boot 4.1.1 · Spring Security 7 · Spring Data JPA (Hibernate 7) · MySQL 8.0 · JWT (HS256, jjwt 0.12.6)

> Version note: built on Spring Boot 4.1.1 following the course Spring Boot 4 setup (`spring-boot-starter-webmvc`, technology-specific test starters, Jackson 3 on the Boot side). Security uses the lambda-style `SecurityFilterChain` bean; no `WebSecurityConfigurerAdapter`.

## Endpoints

| Endpoint | Behavior | Codes |
|---|---|---|
| `POST /api/auth/register` | Creates a user, hashes the password, never returns the hash | 201 created, 400 invalid input, 409 email already registered |
| `POST /api/auth/login` | Verifies credentials, returns a signed JWT with an `exp` claim | 200 with token, 401 on any credential failure |
| `GET /api/users/me` | Protected. Returns the caller's own profile, identified from the token's `sub` claim | 200 with profile, 401 missing / invalid / expired token |

## How to run (IntelliJ + MySQL)

### 1. MySQL setup
The application expects a MySQL 8.0 server on `localhost:3306`, a database named **`gameflix`**, and the MySQL user **`root`** (password set in the local settings file below). A second database, **`gameflix_test`**, is used only by the automated tests so they never touch real data.

In MySQL Workbench (connection: host `127.0.0.1`, port `3306`, user `root`) run:

```sql
CREATE DATABASE IF NOT EXISTS gameflix CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gameflix_test CHARACTER SET utf8mb4;
```

The `users` table is created automatically on first start (see `ddl-auto` below). A dedicated, non-root user also works: create it with `CREATE USER`, grant it `ALL` on both databases, and use its name and password in the next step.

### 2. Secrets (never committed)
Create `src/main/resources/application-local.properties` (it is listed in `.gitignore`):

```properties
MYSQL_USER=root
MYSQL_PASSWORD=your_mysql_password
JWT_SECRET=the_base64_value_from_the_command_below
```

Replace the password with your own MySQL password and the secret with the command output. Generate a JWT secret in PowerShell:

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

`application.properties.example` shows the same keys with placeholder values. The same three values can instead be set as environment variables in the IntelliJ run configuration. Nothing secret is hardcoded in any committed file.

### 3. Run
1. Open the project folder in IntelliJ as a Maven project with the **Temurin 21** SDK, and reload Maven.
2. Run `GameflixApplication`. The API starts on `http://localhost:8080`.
3. Run the tests: right-click `AuthFlowTest` > Run (they use the `gameflix_test` database, the same local settings file, and a random per-run JWT key).

## Schema

Also provided in `schema.sql`. Output of `SHOW CREATE TABLE users;` from the running MySQL instance:

```sql
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(50) NOT NULL,
  `email` varchar(254) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
```

- **`password VARCHAR(255)`** is sized for the hash, not the password. A bcrypt hash is 60 characters, 67–68 with the `{bcrypt}` prefix of the delegating encoder, so a shorter column would silently truncate it and break every login.
- **`UNIQUE KEY uk_users_email`** enforces email uniqueness in the database. `AuthService.register` uses `saveAndFlush` and turns the resulting `DataIntegrityViolationException` into a 409, so two concurrent registrations are decided by the constraint rather than by a check-then-insert race. Emails are trimmed and lower-cased before they are stored.

## `spring.jpa.hibernate.ddl-auto`

**`update`.** Hibernate creates or adjusts the `users` table on startup and keeps the data between restarts, which is needed to produce test evidence such as the `SELECT` screenshot. `create-drop` would wipe all users every time the app stops.

## Password hashing

**Algorithm: bcrypt, through a `DelegatingPasswordEncoder`, work factor (cost) 12.**

bcrypt is one of the adaptive algorithms named in the OWASP Password Storage Cheat Sheet and ships with `spring-boot-starter-security`, so no extra dependency is needed. I chose cost 12 instead of Spring's default of 10 because every +1 doubles the work an attacker must do per guess, while a full login request still measured about **224 ms** on my machine (`Measure-Command` around a complete `POST /api/auth/login`, including the HTTP call and database lookup, so the bcrypt check is most of that), which is acceptable for a login. The delegating encoder stores each hash with its algorithm prefix (`{bcrypt}$2a$12$...`), so the algorithm and cost are recorded with the hash and can be migrated later without invalidating existing users.

Because bcrypt truncates its input at 72 bytes, registration enforces a password length of 8 to 64 characters. No plaintext password is persisted, returned in a response, or logged: the request records override `toString`, the validation error handler never echoes rejected values, SQL logging is off, and no request-body logging is enabled.

## JWT

- Signed with HS256. The key is read from the `JWT_SECRET` setting (Base64, at least 32 bytes); the application refuses to start if it is missing or too short.
- Claims: `sub` (user id), `iss`, `iat`, `exp`. The payload is only Base64URL-encoded, not encrypted, so nothing confidential (no email, password, or hash) is placed in it.
- **Lifetime: 30 minutes.** That is long enough for a normal session without constant re-login, and short enough that a leaked token is useful to an attacker only briefly. There is no refresh token in this assignment, so the user logs in again after it expires.
- Validation (`JwtService.validateAndGetUserId`) uses `parseSignedClaims`, which verifies the signature and expiry **before** any claim is trusted. Unsigned, wrongly signed, tampered and expired tokens are all rejected, and the filter answers 401.

## Other security decisions

- **Generic login failure:** a wrong password, an unknown email, or blank fields all return `401 {"error":"Invalid email or password"}`. A dummy hash is checked when the email is unknown so response timing does not reveal which addresses are registered.
- **CORS** allows exactly one origin (`app.cors.allowed-origin`, default `http://localhost:3000`); the app refuses to start if the value contains `*`.
- Stateless sessions; CSRF protection is disabled because the token travels in the `Authorization` header and no cookies are used; Spring Boot's default generated login user is disabled.

## Test evidence

Evidence option chosen: **automated tests** (`@SpringBootTest` with MockMvc against MySQL), in `src/test/java/com/gameflix/AuthFlowTest.java`, plus manual PowerShell calls against the running server. Screenshots are in the accompanying Word document.

| Required case | Test method | Expected |
|---|---|---|
| Successful registration | `registerSucceeds_andStoresHashNotPassword` | 201, no password or hash in the response, stored value is a `{bcrypt}` hash |
| Duplicate-email rejection | `duplicateEmailIsRejectedWith409` | 409 (also for the same email in different case) |
| Invalid input | `invalidInputIs400` | 400 |
| Successful login returning a token | `loginSucceedsAndReturnsToken` | 200 with token |
| Login with a wrong password | `wrongPasswordIs401_withSameMessageAsUnknownEmail` | 401, identical body to an unknown email |
| Protected call with a valid token | `meWithValidTokenReturnsOwnProfile` | 200 with the caller's profile |
| Protected call, missing token | `meWithoutTokenIs401` | 401 |
| Protected call, tampered token | `meWithTamperedTokenIs401` | 401 |
| Protected call, expired token (extra) | `meWithExpiredTokenIs401` | 401 |

Database proof: `SELECT id, email, password FROM users;` against the `gameflix` database shows `{bcrypt}$2a$12$...` values, never the original password.

## Module 3 design-change note

My original Module 3 ERD (`docs/module3-erd.png`) is below.

![Module 3 ERD](docs/module3-erd.png)

The diagram has four tables: Users, Games, Genres and Reviews. Genres sit in their own table and link to Games through `Games.genre_id`, so a genre is never repeated (one genre to many games). Reviews bridge Users and Games, with a one-to-many relationship from each (`Reviews.user_id` and `Reviews.game_id`). My written design description also includes a UserLibrary table that resolves the many-to-many relationship between users and games; that table is not drawn in the diagram. The design follows Third Normal Form (3NF): data is not duplicated, attributes are atomic, and every non-key attribute depends only on its table's primary key.

Module 4 implements only the **Users** entity, the account data used by the User Service for registration, authentication and profile management. Games, Genres, Reviews and UserLibrary are not part of this assignment and are unchanged. Here is the comparison between the Users table in the ERD and the table I built:

| ERD (Module 3) | Implemented (Module 4) | Change and reason |
|---|---|---|
| `user_id` int, primary key | `id` `BIGINT` `AUTO_INCREMENT`, primary key | Renamed and widened. JPA maps it to a Java `Long`, and the same id is carried in the JWT `sub` claim. Reviews will need a matching `BIGINT` `user_id` foreign key pointing at `users.id`. |
| `username` varchar(50), not null, unique | `display_name` varchar(50), not null (not unique) | Replaced. Login uses the email, so the user does not need a unique username. `display_name` is only shown on the profile returned by `GET /api/users/me`, and the unique key moved to `email` only. |
| `email` varchar(100), not null, unique | `email` varchar(254), not null, **unique key `uk_users_email`** | Widened to 254, the maximum length of a valid email address, so no valid address is rejected. Uniqueness is enforced by the database constraint, which turns concurrent duplicate registrations into a 409. Emails are stored trimmed and lower-cased. |
| `password_hash` varchar(255), not null | `password` varchar(255), not null | Column renamed to `password` to match the assignment's `SELECT id, email, password` check. It still stores only the hash (`{bcrypt}$2a$12$...`), and `VARCHAR(255)` was already the right size, so nothing is truncated. |
| `created_at` timestamp | `created_at` datetime(6), **not null** | Now required and set automatically by the application when the row is saved. `datetime(6)` keeps microsecond precision and avoids the 2038 limit of MySQL `TIMESTAMP`. |

What stayed the same: the password is never stored in plaintext, the table remains in 3NF (every non-key column depends only on the primary key), and `Users` keeps its one-to-many relationship to Reviews. The main design change is that the ERD identified a user by `username` and `email`, while the implementation identifies a user only by `email`. The database now enforces that, which is also what the 409 duplicate check relies on.

## Docker (Module 5)

The backend and MySQL run as two containers via Docker Compose. See [`DOCKER.md`](DOCKER.md) for the directive-by-directive explanation of the `Dockerfile` and `compose.yaml`, plus run and verify steps.

```powershell
Copy-Item .env.example .env   # fill in passwords and JWT_SECRET (.env is git-ignored)
docker compose up --build -d
docker compose ps
```

## AI-use section

I used Claude to generate the code for this assignment and also had it refine the README so that it produced a easy to read version rather then a bunch of code. The most important change that I had to do was changing the config files so that it would properly use the correct version of intellij, with the correct SDK, Change the orientation of the files so that they were readable for a human, and generate the SQL code so that I could link it in the README. I also had to go though some of the AuthFlowTest and tweak some of the lines/test so that they hit all of the requirements detailed in the assignment. I also had to code the test case for a successful login returning a token becasue the AI generated code missed that requirement.