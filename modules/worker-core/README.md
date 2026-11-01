# worker-core

Composable, schedulable workers.

## Usage

```kotlin
val worker = Worker(id = "api-1") {
    config {
        defaults { // default values for `job {}` blocks 
            pollInterval = 5.seconds
            retry = RetryPolicy(maxAttempts = 5, initialDelay = 15.seconds)
        }
    }

    jobs(InMemoryJobQueue(Json)) {
        job<EmailConfig> { context, config ->
            handler {
                // context.runKey is stable across retries of this occurrence.
                send(config.email, config.content)
            }
        }

        job<TimedEmailConfig> { context, config ->
            val database by resource { Database.connect() }
                .onDispose { it.close() }

            database.validateEmail(config.email)

            handler {
                send(config.email, config.content)
            }
        }
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

worker.cancel(jobId)                         // pending or running
```

Jobs are selected by the serial name of their config type. Add `@SerialName` to the config class to
keep that name stable when the class is moved or renamed.

## Queues and history

Each `jobs(queue) { ... }` block binds a `JobQueue` to the handlers it may execute. A worker can use
several queues, and each block polls independently with its own settings:

```kotlin
val worker = Worker(id = "api-1") {
    jobs(sharedQueue) {
        job<SendEmail> { context, config -> handler { send(config) } }
    }
    jobs(InMemoryJobQueue(Json), config = { pollInterval = 1.seconds }) {
        job<PruneCache> { context, config -> handler { cache.evictAll() } }
    }
}
```

Blocks start from the worker's defaults and may override `pollInterval`, `prepareWindow`,
`leaseDuration`, `heartbeatInterval`, `maxConcurrentJobs`, and the default `retry` policy:

```kotlin
config {
    defaults {
        pollInterval = 5.seconds
        prepareWindow = 30.seconds
    }
}
```

A queue only holds pending and running jobs. Terminal occurrences leave it: completed one-shot jobs
and failed jobs are removed, while a recurring job keeps its single pending row between occurrences.

Pass a `JobHistory` to record finished attempts:

```kotlin
val history = InMemoryJobHistory()

val worker = Worker(id = "api-1") {
    jobs(sharedQueue, history) {
        job<SendEmail> { context, config -> handler { send(config) } }
    }
    jobs(localQueue, history) {
        job<PruneCache> { context, config -> handler { cache.evictAll() } }
    }
}
```

History is optional and write-only by default; `JobHistory.Readable` adds `list` for querying.
A handler may only be registered once across all queues and blocks, and each block records to its
configured history.

## Handlers

A `job` block runs as soon as the job is claimed, during the block's `prepareWindow`.
Everything here executes on claim, meaning anything you place before the actual `handler {}` may be run multiple times.
For this reason only the `handler` should mutate state.

```kotlin
job<TimedEmailConfig> { context, config ->
    val database by resource { Database.connect() }
        .onDispose { it.close() }

    database.validateEmail(config.email)
    handler { send(config.email, config.content) }
}
```

## Resources

`job` blocks can use `resource` and `defer`:

- `resource { ... }` acquires a resource immediately. `onDispose { ... }` registers its cleanup.
- `defer { ... }` registers an action to perform after the job is completed or failed.
- Resources are disposed in reverse acquisition order.
- If preparation itself fails, resources acquired so far are disposed before the job retries.

## Scheduling

- `Schedule.Once(at)` runs once at an exact instant. Jobs are claimed and prepared during the
  block's `prepareWindow` and start no earlier than `at`. `LateExecutionPolicy` can fail an
  occurrence that starts too late (for example after its worker died); a retry whose instant would
  exceed the tolerance fails the job instead of being scheduled.
- `Schedule.FixedRate(interval, startAt)` runs at a fixed cadence and skips missed occurrences.
- `Schedule.FixedDelay(interval)` waits after each completion.
- `Schedule.Cron(expression, timeZoneId)` accepts modern seven-field cron expressions through the
  `cron` module. Day-of-week numbering is `1=SUN` through `7=SAT`, and `?`, `L`, `W`, and `#` are
  supported.

Use `Schedule.firstOccurrence` and `Schedule.nextOccurrence` when inserting or rescheduling rows
outside `Worker.sync`.

`prepareWindow` should cover the time a handler needs to finish preparation. A claimed job keeps a
slot in its block's `maxConcurrentJobs` while waiting, so that execution can start on time.

## Multi-server behavior

A job is only executed after `JobQueue.claim` atomically assigns it to a worker. The worker renews
its lease while preparing and executing. If a handler throws, or its lease expires because the
worker died, the job becomes runnable again after the configured retry delay.

Execution is **at-least-once**. A process can die after performing side effects but before marking
the job complete, so handlers must be idempotent. Use `Handler.Context.runKey` for deduplication.

## Cancellation

`worker.cancel(id)` removes a pending job immediately. A claimed job is cancelled cooperatively,
whether it is preparing, waiting for its scheduled instant, or executing: prepared resources are
disposed and the job leaves the queue without another attempt. Cancelling a recurring job prevents
its next occurrence.

Cancelling a running job returns once the request is recorded; the handler unwinds asynchronously,
so it must suspend for cancellation to take effect. The worker that owns the job notices through its
heartbeat, and the lease sweeper applies the cancellation when the owner is gone. Each cancelled
attempt is recorded in `JobHistory` with `Status.CANCELLED`, as is a pending job that never started.

## Finalization

Chain `finalizedBy` onto `handler {}` to run code after the job has left the queue, before prepared
resources are disposed - so the finalizer can still use them:

```kotlin
job<SendEmail> { _, config ->
    val metrics by resource { Metrics.connect() }
    handler { send(config) } finalizedBy { outcome ->
        metrics.record(config, outcome)
    }
}
```


## Queue contract

Database queues must implement these atomicity rules:

1. `claim` selects up to `limit` enabled `PENDING` jobs with a supported `handler_id` and
   `next_run_at <= now + prepareWindow`, earliest due first.
2. It atomically updates each of those rows to `RUNNING`, assigns a fresh lock (`lock_token`,
   `locked_by`, `lease_expires_at`), and increments `attempt`.
3. A row whose conditional update affects no rows was claimed by another worker; skip it.
4. `renew`, `complete`, `reschedule` and `fail` must only update a row while its `lock_token` still
   matches.
5. `renew` reports `CANCELLATION_REQUESTED` instead of extending the lease once
   `requestCancellation` flagged the row.
6. `requestCancellation` removes a `PENDING` row, or flags a `RUNNING` one for its owner. The flag
   survives rescheduling, so a row released before its owner noticed is still canceled when claimed
   again.
7. `expired` returns rows with `status = RUNNING` and `lease_expires_at <= now`, including their
   cancellation flag. The worker conditionally reschedules, fails, or cancels them; never claim an
   expired row directly, because the retry delay must be respected.
8. A `RUNNING` job always has a complete lock: token, owner, and expiration are all present. Queues
   should map the three lock columns together so a partial lock cannot be persisted.
9. `complete` without a next run and `fail` remove the row; `complete` with a next run and
   `reschedule` return the row to `PENDING`.
10. `create` and `update` receive the config already encoded by the queue's `ConfigCodec`;
    `codec.decode` turns a claimed job's payload back into its config. `StringConfigCodec` wraps a
    `StringFormat`, while `DirectConfigCodec` stores the config instance itself.

`worker-exposed` provides Exposed-based implementations of this contract and of `JobHistory`.

`payload`, `schedule`, `retry_policy` and `late_policy` can be stored as text. The queue stores
payloads as produced by its `codec`; with `StringConfigCodec` the payload is the handler config
encoded by its `StringFormat`. The job definition itself contains no payload. A JSON job looks
like:

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

Execution records live in `JobHistory`, not in the queue. Pruning history is the history store's
responsibility.
