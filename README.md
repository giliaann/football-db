# Football Competitions Database

PostgreSQL database for managing football competitions, with Adminer as a web UI. The schema from `ddl_script.sql` is loaded when the database is first created.

## Setup

Create a `.env` file in the project root:

```env
POSTGRES_DB=football
POSTGRES_PASSWORD=change_me
```

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

## Access

- PostgreSQL: `localhost:5432`
- Adminer: <http://localhost:8080> (System: `PostgreSQL`, Server: `db`, Username: `postgres`, and the password and database from `.env`)
