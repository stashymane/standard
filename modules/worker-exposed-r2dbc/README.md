# worker-exposed-r2dbc

Exposed R2DBC persistence for `worker-core`.

## Usage

```kotlin
val database = R2dbcDatabase.connect("r2dbc:postgresql://localhost:5432/app")
val queue = ExposedJobQueue(database, Json)
val history = ExposedJobHistory(database)

// Run once through the application's migration flow, before starting the worker.
suspendTransaction(database) {
    SchemaUtils.create(WorkerJobsTable, WorkerJobHistoryTable)
}

val worker = Worker(id = "api-1") {
    jobs(queue, history) {
        job<EmailConfig> { context, config -> send(config) }
    }
}
worker.start(scope)
```
