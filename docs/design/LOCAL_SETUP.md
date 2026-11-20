# Local Setup and Verification

Prerequisites: Java 21, Maven 3.9+, Docker Desktop Linux engine and Python 3 for
documentation checks. Backend is Spring Boot 3.5.15; dependency versions are managed
by its BOM except Flyway 11.20.3, pinned for PostgreSQL 18 compatibility. Tests use JUnit 5 for application integration; the separate planned
portfolio API/UI framework remains REST Assured/Selenium + TestNG.

## Verify without creating a development database

From project root:

```powershell
python tools/check_docs.py
mvn -f backend/pom.xml verify
```

`mvn test` runs fast unit tests. **`mvn verify` is required** to run HTTP integration
tests through Failsafe. Tests start an isolated PostgreSQL 18 container, apply the
real Flyway migration, generate credentials and clean up that container. They do
not use a pre-existing development database. No tests are skipped when Docker is
unavailable: integration should fail visibly rather than masquerade as coverage.

If this Windows environment reports `PKIX path building failed`, use its Windows
trust store while retaining TLS verification:

```powershell
mvn -f backend/pom.xml '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' verify
```

This is an environment workaround, not a requirement on other operating systems.
Build outputs and test XML are ignored under `backend/target/`; small curated
execution summaries belong in the worklog. No CI or UI pass is implied.

## Run the first auth/account slice

Set process environment variables using private values (do not commit `.env` or
paste secrets into logs). `.env.example` lists names; Java does not automatically
load a Compose `.env` file. Required: DB_USERNAME=`quizmaster`, DB_PASSWORD,
DB_URL=`jdbc:postgresql://localhost:5432/quizmaster` and JWT_SECRET_BASE64 containing
at least 32 cryptographically random decoded bytes. Generate the JWT key in memory:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
docker compose up -d --wait db
```

Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD (12–128 characters), then
start **once against an empty account table** with:

```powershell
mvn -f backend/pom.xml spring-boot:run '-Dspring-boot.run.profiles=bootstrap'
```

The opt-in profile refuses a nonempty account table. After the first run, disable
that profile and unset bootstrap variables. Subsequent startup:

```powershell
mvn -f backend/pom.xml spring-boot:run
```

API binds to `127.0.0.1:8080` by default. POST `/api/v1/auth/login` using runtime
credentials; use its bearer token for GET `/api/v1/auth/me` and admin `/users`
operations. See API_CONTRACT for payloads. No public signup, default password,
password recovery, refresh tokens or UI exists in this slice.

Stop services with `docker compose stop db`; preserve the named development data
volume. Testcontainers cleanup affects only its own disposable test resources.
No production reset, remote push or deployment is part of this workflow.

Flyway compatibility reference: the [11.20.3 PostgreSQL adapter source](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java)
recognizes major version 18. The first test run found Boot's default 11.7.2 warning
about PostgreSQL 18; the explicit override addresses that observed mismatch.
