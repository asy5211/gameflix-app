# GameFlix – Dockerized Backend (Module 5)

Two containers started by Docker Compose: **mysql** (MySQL 8.0 + named volume) and **app** (the Spring Boot 4.1.1 / Java 21 JAR).

## Files (all in the project root, next to `pom.xml`)

| File | Committed? | Purpose |
|---|---|---|
| `Dockerfile` | yes | Builds the app image |
| `.dockerignore` | yes | Keeps secrets and junk out of the build context |
| `compose.yaml` | yes | Defines the MySQL + app stack |
| `.env.example` | yes | Template of the variables Compose needs |
| `.env` | **no** (git-ignored) | Your real values; copy from `.env.example` |

## Dockerfile – what each directive does

| Directive | Explanation |
|---|---|
| `FROM maven:3.9-eclipse-temurin-21 AS build` | Starts stage 1 from an image that has Maven and the full JDK 21. `AS build` names the stage so stage 2 can copy from it. This stage only exists to compile the project. |
| `WORKDIR /build` | Sets the folder inside the image where later commands run (created if missing). |
| `COPY pom.xml .` | Copies the Maven build file into `/build`. |
| `COPY src ./src` | Copies the source code. Because `.dockerignore` removes `application-local.properties`, the local secrets file is never copied and cannot end up in the JAR. |
| `RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests clean package` | Builds the executable JAR. `-B` = non-interactive output, `-q` = quiet, `-DskipTests` = skip tests (they need a separate `gameflix_test` database that does not exist during an image build; run them in IntelliJ or CI). The cache mount keeps downloaded dependencies between builds so rebuilds are fast, without storing them in the final image. |
| `FROM eclipse-temurin:21-jre-alpine` | Starts stage 2 from a small image with only the Java 21 *runtime* (JRE). Compilers, Maven and source code are left behind in stage 1, so the final image is smaller and has less attack surface. Java 21 matches `java.version` in `pom.xml`. |
| `WORKDIR /app` | Working folder for the running app. |
| `RUN addgroup -S spring && adduser -S spring -G spring` | Creates a non-privileged system user and group (Alpine syntax) so the app does not run as root. |
| `COPY --from=build /build/target/*.jar app.jar` | Copies only the finished JAR out of stage 1 and renames it `app.jar`. Spring Boot's Maven plugin produces exactly one `.jar` in `target/`. |
| `USER spring` | Every later instruction and the running container use the `spring` user instead of root. If the app were compromised, the attacker would have far fewer rights. |
| `EXPOSE 8080` | Documents that the app listens on port 8080. It does not publish the port; `ports:` in `compose.yaml` does that. |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | The command that runs when the container starts. Exec form (JSON array) makes `java` PID 1, so `docker stop` signals reach Spring Boot and it shuts down gracefully. |

### `.dockerignore`
Lists what is **not** sent to Docker as build context: `.git`, `.idea`, `*.iml`, logs, `target` (rebuilt inside Docker), `.env`, the Postman folders, and `application-local.properties`. This keeps the image small and keeps secrets out of it.

## compose.yaml – key settings

| Setting | Explanation |
|---|---|
| `mysql: image: mysql:8.0` | Official MySQL 8.0 image (same major version as the local install). |
| `MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD` | On first start with an empty volume, MySQL creates the `gameflix` database and a non-root app user with access to it. Values come from `.env`. |
| `ports: "3307:3306"` | Host port 3307 → container port 3306, so it does not clash with the MySQL already running on your PC at 3306. |
| `volumes: mysql_data:/var/lib/mysql` | Named volume that keeps database files when containers are stopped or recreated. |
| `healthcheck` | Runs `mysqladmin ping` until MySQL accepts connections. `$$` stops Compose from substituting the variable on the host; the container's own variable is used instead. |
| `app: build` | Builds the image from the `Dockerfile` in this folder. |
| `DB_URL` | Overrides the default in `application.properties`. The host is `mysql` (the Compose service name), because `localhost` inside the app container means the app container itself. |
| `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGIN` | The exact variable names `application.properties` already reads. Environment variables beat `application-local.properties`, and nothing secret is baked into the image. |
| `depends_on: condition: service_healthy` | Starts the app only after MySQL is healthy. |
| `networks: app-net` | Private network both services share. |

## Run it (PowerShell, from the project root)

```powershell
Copy-Item .env.example .env        # then edit .env: set passwords and JWT_SECRET
docker compose config              # validate (output shows passwords - do not screenshot/share)
docker compose up --build -d
docker compose ps                  # mysql: healthy, app: running
docker compose logs -f app         # Ctrl+C stops following, not the containers
```

Before starting, stop `GameflixApplication` in IntelliJ so port 8080 is free.

## Verify (the strongest proof is a real database write)

```powershell
$base = "http://localhost:8080"
Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' -Body '{"email":"docker@example.com","password":"Throwaway#Pass1","displayName":"Docker"}'
$t = (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body '{"email":"docker@example.com","password":"Throwaway#Pass1"}').token
Invoke-RestMethod -Uri "$base/api/users/me" -Headers @{Authorization="Bearer $t"}
docker compose exec mysql mysql -ugameflix_user -p gameflix -e "SELECT id,email,password FROM users;"
```

The last command prompts for `MYSQL_PASSWORD` from `.env`. Expect the new row with a `{bcrypt}...` hash. (Workbench: host `127.0.0.1`, port `3307`.)

## Stop / reset

```powershell
docker compose stop        # stop, keep containers
docker compose down        # remove containers, keep the database volume
docker compose down -v     # also delete the volume (wipes the Docker database)
```

The Docker MySQL (port 3307) and the local MySQL (port 3306) are separate servers; users created in one do not appear in the other.
