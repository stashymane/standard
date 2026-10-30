# worker-exposed

Exposed JDBC persistence for `worker-core`.

## Usage

```kotlin
val database = Database.connect(dataSource)
val queue = ExposedJobQueue(database, Json)
val history = ExposedJobHistory(database)

// Run once through the application's migration flow, before starting the worker.
transaction(database) {
    SchemaUtils.create(WorkerJobsTable, WorkerJobHistoryTable)
}

val worker = Worker(id = "api-1") {
    jobs(queue, history) {
        job<EmailConfig> { context, config -> send(config) }
    }
}
worker.start(scope)
```

`ExposedJobQueue` and `ExposedJobHistory` use the supplied `Database` and never create, own, or close
connections. The schema is user-managed; `WorkerJobsTable` and `WorkerJobHistoryTable` are the source
of truth, and `createSchema()` on either store is an optional convenience for applications without a
migration system.

The module is JDBC-only. The queue stores handler payloads and job metadata with the supplied
`StringFormat`, and uses Exposed's `timestamp()` columns for `kotlin.time.Instant`.

On PostgreSQL, MySQL 8+/MariaDB 10.6+, and Oracle, claim candidates are selected with
`FOR UPDATE SKIP LOCKED`, so concurrent workers claim disjoint jobs without colliding on the
earliest due rows. Other dialects (H2, SQLite, SQL Server) use optimistic updates that skip rows
claimed concurrently — correct, but less efficient under heavy multi-worker contention.
