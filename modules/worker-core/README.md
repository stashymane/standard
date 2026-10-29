# worker-core

Composable, schedulable workers.

## Usage

```kotlin
val store = InMemoryJobStore(Json { ignoreUnknownKeys = true })

val worker = Worker(id = "api-1", store = store) {
    jobs {
        job<EmailConfig> { context, config ->
            // context.runKey is stable across retries of this occurrence.
            send(config.email, config.content)
        }

        prepared<TimedEmailConfig> { context, config ->
            val database by resource { Database.connect() }
                .onDispose { it.close() }

            database.validateEmail(config.email)

            handler {
                send(config.email, config.content)
            }
        }
    }
    config {
        pollInterval = 5.seconds
        retry = RetryPolicy(maxAttempts = 5, initialDelay = 15.seconds)
    }
}
worker.start(scope)

worker.schedule("nightly-email", EmailConfig(...)) {
    cron("0 0 3 ? * * *")
    latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
    retry = RetryPolicy(maxAttempts = 5, initialDelay = 15.seconds)
}

worker.enqueue(EmailConfig(...))              // as soon as possible
worker.enqueue(EmailConfig(...), at = when)  // exact instant
```

Jobs are selected by the serial name of their config type. Add `@SerialName` to the config class to
keep that name stable when the class is moved or renamed.

## Preparation

A `job` block runs at the scheduled instant.

A `prepared` block runs as soon as the job is claimed, during `prepareWindow`. It must end by
returning `handler {}`; that block runs at the scheduled instant.

```kotlin
prepared<TimedEmailConfig> { context, config ->
    val database by resource { Database.connect() }
        .onDispose { it.close() }

    database.validateEmail(config.email)
    handler { send(config.email, config.content) }
}
```

## Resources

Both `job` and `prepared` blocks can use `resource` and `defer`:

- `resource { ... }` acquires a resource immediately. `onDispose { ... }` registers its cleanup.
- `defer { ... }` registers an action to perform after the job is completed or failed.
- Resources are disposed in reverse acquisition order.
- If preparation itself fails, resources acquired so far are disposed before the job retries.

## Scheduling

- `Schedule.Once(at)` runs once at an exact instant. Jobs are claimed and prepared during
  `prepareWindow` and start no earlier than `at`. `LateExecutionPolicy` can fail an occurrence that
  starts too late (for example after its worker died); a retry whose instant would exceed the
  tolerance fails the job instead of being scheduled.
- `Schedule.FixedRate(interval, startAt)` runs at a fixed cadence and skips missed occurrences.
- `Schedule.FixedDelay(interval)` waits after each completion.
- `Schedule.Cron(expression, timeZoneId)` accepts modern seven-field cron expressions through the
  `cron` module. Day-of-week numbering is `1=SUN` through `7=SAT`, and `?`, `L`, `W`, and `#` are
  supported.

Use `Schedule.firstOccurrence` and `Schedule.nextOccurrence` when inserting or rescheduling rows
outside `Worker.sync`.

`prepareWindow` should cover the time a prepared handler needs to finish preparation. A prepared job
keeps its `maxConcurrentJobs` slot while waiting, so that execution can start on time.

## Multi-server behavior

A job is only executed after `JobStore.claim` atomically assigns it to a worker. The worker renews
its lease while preparing and executing. If a handler throws, or its lease expires because the
worker died, the job becomes runnable again after the configured retry delay.

Execution is **at-least-once**. A process can die after performing side effects but before marking
the job complete, so handlers must be idempotent. Use `Handler.Context.runKey` for deduplication.

## Store contract

Database stores must implement these atomicity rules:

1. `claim` selects one enabled `PENDING` job with a supported `handler_id` and
   `next_run_at <= now + prepareWindow`.
2. It atomically updates that row to `RUNNING`, assigns a fresh lock (`lock_token`, `locked_by`,
   `lease_expires_at`), and increments `attempt`.
3. If the conditional update affects no rows, return `null` and try another candidate.
4. `renew`, `complete`, `reschedule` and `fail` must only update a row while its `lock_token` still
   matches.
5. `expired` returns rows with `status = RUNNING` and `lease_expires_at <= now`. The worker
   conditionally reschedules or fails them; never claim an expired row directly, because the retry
   delay must be respected.
6. A `RUNNING` job always has a complete lock: token, owner, and expiration are all present. Stores
   should map the three lock columns together so a partial lock cannot be persisted.
7. `create` and `updateDefinition` receive a typed config with its serializer; the store encodes it
   with its own format. `readConfig` decodes a claimed job's config with the handler's serializer.

`worker-exposed` provides an Exposed-based implementation of this contract.

`payload`, `schedule`, `retry_policy` and `late_policy` can be stored as text. The store encodes
handler configs with its own `StringFormat`; the definition itself contains no payload. A JSON
definition looks like:

```json
{
  "id": "nightly-cleanup",
  "handlerId": "cleanup",
  "schedule": {
    "type": "cron",
    "expression": "0 0 3 ? * * *",
    "timeZoneId": "UTC"
  },
  "enabled": true,
  "retry": {
    "maxAttempts": 5,
    "initialDelay": "10s",
    "maxDelay": "10m",
    "multiplier": 2.0,
    "jitter": 0.2
  },
  "latePolicy": {
    "type": "run"
  }
}
```

Completed and failed rows are retained for inspection; pruning them is the store's responsibility.
