# worker-ktor

Binds a `Worker` to the Ktor application lifecycle.

## Usage

```kotlin
install(Workers) {
    id = "api-1"
    jobs(InMemoryJobQueue(Json { ignoreUnknownKeys = true; encodeDefaults = true })) {
        job<EmailConfig> { context, config -> send(config) }
    }
    config {
        pollInterval = 5.seconds
    }
}

application.launch {
    application.workers.schedule("nightly-email", EmailConfig(/* ... */)) {
        schedule = cron("0 0 3 ? * * *")
    }
}
```

Each `jobs` block binds a queue to the handlers it may execute and optionally records their history.
Several blocks may use the same queue, each with its own history. A block admits through a
`ConcurrencyPool`, which can be shared with other blocks:

```kotlin
install(Workers) {
    val smtp = pool("smtp") { capacity = 4 }

    jobs(queue, pool = smtp) {
        job<EmailConfig> { context, config -> handler { send(config) } }
    }
}
```

The plugin exposes the running instance through `application.workers`.

A worker failure that escapes job execution is logged and stops the application so that the host can
restart the process. A failing job handler stays isolated to that execution.
