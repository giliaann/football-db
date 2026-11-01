# Football Competitions Database

PostgreSQL database for managing football competitions, with Adminer as a web UI. The schema from `ddl_script.sql` is loaded when the database is first created.

## Setup

Create a `.env` file in the project root:

```env
POSTGRES_DB=football
POSTGRES_PASSWORD=change_me
```

`DB_PORT` and `ADMINER_PORT` are optional and set the ports published on your machine. They default to `5432` and `8080`.

## Usage

Start the containers:

```bash
docker compose up -d
```

Stop the containers and keep the data:

```bash
docker compose down
```

Stop the containers and delete the data:

```bash
docker compose down -v
```

The schema is only applied to an empty database, so use `down -v` after changing `ddl_script.sql`.

## Test data

The seeder inserts a small, consistent data set into every table using Hibernate through the JPA API. It requires JDK 21 or newer; Maven is downloaded automatically by the wrapper.

Start the database, then run the seeder from the project root:

```bash
docker compose up -d
./mvnw compile exec:java
```

The connection settings come from `.env` or environment variables, with environment variables taking precedence. `POSTGRES_DB` and `POSTGRES_PASSWORD` are required. `DB_HOST`, `DB_PORT` and `DB_USER` default to `localhost`, `5432` and `postgres`. The seeder and `docker-compose.yml` read the same `DB_PORT`, so one value in `.env` sets both.

The seeder only runs on an empty database and otherwise exits without changing anything. To seed again, reset the database with `docker compose down -v`.

## Random data

The random seeder builds a realistic football history from 2023 to 1 November 2026: real competitions, clubs and stadiums, with fictional people. The same seed always gives the same data. `opis_seedera.md` describes its classes and rules.

Fill an empty database (the seed is optional and defaults to 42):

```bash
./mvnw compile exec:java -Dexec.args="random 42"
```

## Access

These are the default addresses. If you set `DB_PORT` or `ADMINER_PORT`, use those ports instead.

- PostgreSQL: `localhost:5432`
- Adminer: <http://localhost:8080> (System: `PostgreSQL`, Server: `db`, Username: `postgres`, and the password and database from `.env`). The server stays `db` whatever `DB_PORT` is set to, because Adminer connects inside the Docker network.
