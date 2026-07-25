# Docker Databases Visualization Guide

This guide shows how to inspect the databases used by Seika when running with Docker Compose.

It covers two ways of working:

- Terminal commands for quick inspection and debugging.
- UI tools such as pgAdmin 4 for PostgreSQL.

For MongoDB, the recommended approach in this project is:

- Use the terminal with `mongosh` for local Docker MongoDB.
- Use MongoDB Atlas UI if the notification database is deployed on Atlas.

MongoDB Compass is not the recommended default for the local Docker setup because the replica set host can be hard to resolve from the host machine.

## 1. PostgreSQL Databases

Seika uses separate PostgreSQL containers for each service:

- `identity-db` on host port `5432`
- `profile-db` on host port `5433`
- `wallet-db` on host port `5434`
- `marketplace-db` on host port `5435`

The database names are:

- `identity-service-seika`
- `profile-service-seika`
- `wallet-service-seika`
- `marketplace-service-seika`

### 1.1 View PostgreSQL from Terminal

You can connect directly with `psql` through the actual Docker container names:

```bash
docker exec -it seika-identity-db-1 psql -U postgres -d identity-service-seika
docker exec -it seika-profile-db-1 psql -U postgres -d profile-service-seika
docker exec -it seika-wallet-db-1 psql -U postgres -d wallet-service-seika
docker exec -it seika-marketplace-db-1 psql -U postgres -d marketplace-service-seika
```

Useful commands inside `psql`:

```sql
\l          -- list databases
\dt         -- list tables in the current database
\d table    -- describe a table
SELECT * FROM "user";
SELECT COUNT(*) FROM "user";
\q          -- quit
```

If you want a one-line query without entering the shell:

```bash
docker exec -it seika-identity-db-1 psql -U postgres -d identity-service-seika -c "SELECT COUNT(*) FROM \"user\";"
```

### 1.2 View PostgreSQL from pgAdmin 4

Create one server entry per database in pgAdmin 4.

Connection settings:

- Host name/address: `localhost`
- Port:
  - `5432` for `identity-db`
  - `5433` for `profile-db`
  - `5434` for `wallet-db`
  - `5435` for `marketplace-db`
- Maintenance database: the matching database name
- Username: `postgres`
- Password: `123` for the local Docker setup

After connecting:

- Expand `Servers`.
- Open `Databases`.
- Open the target database.
- Go to `Schemas` -> `public` -> `Tables`.

If you do not see tables, make sure the service has already created data and the application has started successfully.

## 2. MongoDB Databases

Seika uses MongoDB in Docker for some services and may also use MongoDB Atlas for notification data.

Recommended workflow:

- For local Docker MongoDB, use `mongosh` in the terminal.
- For Atlas, use the Atlas web UI or Atlas connection string in `mongosh`.
- Avoid relying on MongoDB Compass as the primary tool for this project’s local replica set setup.

### 2.1 View MongoDB from Terminal

Connect to the Mongo container:

```bash
docker exec -it seika-mongo-1 mongosh
```

List databases:

```javascript
show databases
```

Switch to a database:

```javascript
use notification-service-seika
```

List collections:

```javascript
show collections
```

Query documents:

```javascript
db.notifications.find().pretty();
db.notifications.countDocuments();
```

Exit the shell:

```javascript
exit;
```

If you want to query MongoDB in one command:

```bash
docker exec -it seika-mongo-1 mongosh --eval "db.getSiblingDB('notification-service-seika').notifications.find().pretty()"
```

### 2.2 View MongoDB Atlas

If notification-service uses Atlas, open the Atlas cluster and browse the database there.

Use the Atlas connection string from `.env`, for example:

```text
mongodb+srv://<user>:<password>@<cluster>/<database>?appName=<app-name>
```

This is the preferred UI option for Atlas-backed data.

### 2.3 Why local MongoDB may not show up in UI

The local MongoDB container runs as a replica set.

Important notes:

- The replica set host may advertise `mongo:27017`, which is resolvable inside Docker but not always from the host machine.
- MongoDB Compass can fail or behave inconsistently with this local replica set setup.
- If you need a GUI, Atlas is usually the safer choice for remote MongoDB data.
- If you are debugging local Docker MongoDB, terminal access with `mongosh` is the most reliable method.

### 2.4 Why a MongoDB database may not appear

MongoDB does not always show a database immediately after it is configured.

In practice:

- A database may not appear until it contains at least one collection.
- A collection may not appear until at least one document has been inserted.
- If the service starts but has not written any data yet, the database may be invisible in UI tools.

So if you do not see the database in MongoDB UI, check whether the service has already created documents.

## 3. Quick Reference

### PostgreSQL

```bash
docker exec -it seika-identity-db-1 psql -U postgres -d identity-service-seika
docker exec -it seika-profile-db-1 psql -U postgres -d profile-service-seika
docker exec -it seika-wallet-db-1 psql -U postgres -d wallet-service-seika
docker exec -it seika-marketplace-db-1 psql -U postgres -d marketplace-service-seika
```

### MongoDB

```bash
docker exec -it seika-mongo-1 mongosh
```

### pgAdmin ports

- `5432` identity
- `5433` profile
- `5434` wallet
- `5435` marketplace

### MongoDB recommendation

- Local Docker: `mongosh`
- Atlas: Atlas UI or `mongosh`
- Compass: not recommended for the local replica set setup

## 4. Troubleshooting

If you do not see data:

1. Confirm the container is running with `docker compose ps`.
2. Confirm the service has started without errors in logs.
3. Check that the correct database name is used.
4. For MongoDB, confirm that at least one collection and one document have been created.
5. For PostgreSQL, confirm the app has actually created tables.

If MongoDB is still empty:

- Verify whether the service is writing to local MongoDB or Atlas.
- Check the current Mongo URI in `.env`.
- Use `mongosh` first before trying a GUI.
