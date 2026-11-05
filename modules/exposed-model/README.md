# exposed-model

Exposed support for the `model` module.

```kotlin
object Events : Table("events") {
    val id = typedId<Event>("id")

    override val primaryKey = PrimaryKey(id)
}
```

## Database-generated IDs

```kotlin
object Events : Table("events") {
    val id = typedId<Event>("id").defaultExpression(IdExpression()).databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}
```

The UUIDv7 function is selected when the default is used:

| Database       | Function         |
|----------------|------------------|
| PostgreSQL 18+ | `uuidv7()`       |
| MariaDB 11.7+  | `UUID_v7()`      |
| H2             | `RANDOM_UUID(7)` |

By default, other dialects fail when the schema is created or the column is otherwise rendered.
To provide a custom expression for generating UUIDs, `IdExpression` accepts a lambda that runs after no supported
expression has been found.
