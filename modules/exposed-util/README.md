# exposed-util

Random utilities for Exposed.

## Enum serial names

```kotlin
@Serializable
enum class Status {
    @SerialName("in-progress")
    IN_PROGRESS,
    DONE,
}

object Jobs : Table("jobs") {
    val status = enumBySerialName<Status>("status", length = 32)
}
```
