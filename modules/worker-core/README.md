# worker-core

Composable, schedulable workers.

## Usage

```kotlin
val store = InMemoryJobStore(Json { ignoreUnknownKeys = true })

val worker = Worker(id = "api-1", store = store) {
    handlers {
        handler<EmailConfig> { context, config ->
            // context.runKey is stable across retries of this occurrence.
            send(config)
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

Handlers are selected by the serial name of their config type. Add `@SerialName` to the config
class to keep that name stable when the class is moved or renamed.

## Scheduling

- `Schedule.Once(at)` runs once at an exact instant. Jobs are claimed and prepared during
  `prepareWindow` and start no earlier than `at`. `LateExecutionPolicy` can fail an occurrence that
  starts too late (for example after its worker died).
- `Schedule.FixedRate(interval, startAt)` runs at a fixed cadence and skips missed occurrences.
- `Schedule.FixedDelay(interval)` waits after each completion.
- `Schedule.Cron(expression, timeZoneId)` accepts modern seven-field cron expressions through the
  `cron` module. Day-of-week numbering is `1=SUN` through `7=SAT`, and `?`, `L`, `W`, and `#` are
  supported.

Use `Schedule.firstOccurrence` and `Schedule.nextOccurrence` when inserting or rescheduling rows
outside `Worker.sync`.

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

Reference schema:

```sql
CREATE TABLE worker_jobs
(
    id               text PRIMARY KEY,
    handler_id       text        NOT NULL,
    payload          text        NOT NULL,
    schedule         text        NOT NULL,
    enabled          boolean     NOT NULL,
    status           text        NOT NULL,
    scheduled_at     timestamptz NOT NULL,
    next_run_at      timestamptz NOT NULL,
    attempt          INTEGER     NOT NULL,
    retry_policy     text        NOT NULL,
    late_policy      text        NOT NULL,
    locked_by        text,
    lock_token       text,
    lease_expires_at timestamptz,
    last_started_at  timestamptz,
    last_finished_at timestamptz,
    last_error       text
);

CREATE INDEX worker_jobs_due ON worker_jobs (status, next_run_at, handler_id);
```

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

Portable claim algorithm:

```sql
SELECT id
FROM worker_jobs
WHERE status = 'PENDING'
  AND enabled
  AND handler_id IN (:supportedHandlerIds)
  AND next_run_at <= :now + :prepareWindow
ORDER BY next_run_at limit 1;

UPDATE worker_jobs
SET status           = 'RUNNING',
    locked_by        = :workerId,
    lock_token       = :token,
    attempt          = attempt + 1,
    lease_expires_at = :now + :lease,
    last_started_at  = :now
WHERE id = :id
  AND status = 'PENDING';
```

If the update affects zero rows, another worker won the race. Postgres/MySQL stores may instead use
`SELECT ... FOR UPDATE SKIP LOCKED`, but the conditional update above also works on SQLite.

Completed and failed rows are retained for inspection; pruning them is the store's responsibility.
