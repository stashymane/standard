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
        cron("0 0 3 ? * * *")
    }
}
```

Each `jobs` block binds a queue to the handlers it may execute and optionally records their history.
The plugin exposes the running instance through `application.workers`.
