# worker-ktor

Binds a `Worker` to the Ktor application lifecycle.

## Usage

```kotlin
install(Workers) {
    id = "api-1"
    store = InMemoryJobStore(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    jobs {
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

The plugin requires `store` and exposes the running instance through `application.workers`.
