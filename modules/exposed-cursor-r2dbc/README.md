# exposed-cursor-r2dbc

Cursor paging for Exposed R2DBC queries.

```kotlin
val page = database.transaction {
    Posts.selectAll()
        .applyCursor(
            column = Posts.id,
            cursor = request.cursor,
            order = SortOrder.DESC,
        )
        .limit(20)
        .map { it[Posts.id] }
        .toList()
}
```
