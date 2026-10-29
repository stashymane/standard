# worker-exposed

Exposed JDBC persistence for `worker-core`.

## Usage

```kotlin
val database = Database.connect(dataSource)
val store = ExposedJobStore(database, Json)

// Run once through the application's migration flow, before starting the worker.
transaction(database) {
    SchemaUtils.create(WorkerJobsTable)
}

val worker = Worker(id = "api-1", store = store) {
    jobs {
        job<EmailConfig> { context, config -> send(config) }
    }
}
worker.start(scope)
```

`ExposedJobStore` uses the supplied `Database` and never creates, owns, or closes connections. The
schema is user-managed; `WorkerJobsTable` is the source of truth, and `store.createSchema()` is an
optional convenience for applications without a migration system.

The module is JDBC-only. The store encodes handler configs and job metadata with the supplied
`StringFormat`, and uses Exposed's `timestamp()` columns for `kotlin.time.Instant`.

On PostgreSQL, MySQL 8+/MariaDB 10.6+, and Oracle, claim candidates are selected with
`FOR UPDATE SKIP LOCKED`, so concurrent workers claim disjoint jobs without colliding on the
earliest due rows. Other dialects (H2, SQLite, SQL Server) use optimistic updates that skip rows
claimed concurrently — correct, but less efficient under heavy multi-worker contention.
