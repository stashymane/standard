# exposed-cursor

Cursor-based paging for Exposed.

```kotlin
val next = Posts.selectAll()
    .where { Posts.id.cursorAfter(lastId, SortOrder.DESC) }
    .orderBy(Posts.id, SortOrder.DESC)
    .limit(20)
```
