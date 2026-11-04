# worker-exposed-jdbc

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
