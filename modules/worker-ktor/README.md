# worker-ktor

Binds a `Worker` to the Ktor application lifecycle.

## Usage

```kotlin
install(Workers) {
    id = "api-1"
    store = InMemoryJobStore(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    handlers {
        handler<EmailConfig> { context, config -> send(config) }
    }
    config {
        pollInterval = 5.seconds
    }
    job<EmailConfig>("nightly-email", EmailConfig(/* ... */)) {
        cron("0 0 3 ? * * *")
    }
}
```

The plugin requires `store` and exposes the running instance through `application.workers`.
Job payloads are encoded by the store, so any format supported by `kotlinx.serialization` can be
used. Handler and scheduling APIs are documented in `worker-core`.
