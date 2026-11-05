# exposed-cursor-jdbc

Cursor paging for Exposed JDBC queries.

```kotlin
val page = transaction {
    Posts.selectAll()
        .applyCursor(
            column = Posts.id,
            cursor = request.cursor,
            order = SortOrder.DESC,
        )
        .limit(20)
        .map { it[Posts.id] }
}
```
