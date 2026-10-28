# cron

Immutable modern cron expressions.

Classic 5-field cron is not supported. Expressions use seven fields:

`seconds minutes hours day-of-month month day-of-week year`

- `*`, `?`, lists, ranges, and steps (`*/n`, `a-b/n`, `a/n`)
- Months `JAN`–`DEC`, weekdays `SUN`–`SAT`, case-insensitive
- Numeric weekdays follow `1=SUN` through `7=SAT`
- Day-of-month: `L`, `LW`, `L-n`, `nW`
- Day-of-week: `nL`, `n#k`
- Years: the full `LocalDate` range (`-999999999..999999999`)
- Day-of-month and day-of-week are mutually exclusive; use `?` for the unused one

## Parsing

```kotlin
val expression = Cron.parse("0 0 3 ? * MON-FRI *")
val next = expression.next() // defaults to next since `Clock.System.now()` in UTC
```

## Builder

```kotlin
val expression = Cron {
    seconds { at(0) }
    minutes { every(15) }
    hours { between(9, 17) }
    daysOfWeek { between(DayOfWeek.MONDAY, DayOfWeek.FRIDAY) }
    months { on(Month.JANUARY, Month.JULY) }
    years { on(2026) }
}
```

Weekday ranges follow cron ordering (`SUN` through `SAT`), matching the expression syntax.
Builder defaults are `0` for seconds and unrestricted values for all other fields.

## Occurrences

`next` returns the first matching wall-clock time or instant strictly after the input.
Searches at most 10 years beyond its input and returns `null` when no occurrence is found in that window.

`asSequence` lazily yields subsequent occurrences.

- Leap days, month lengths, last weekdays, and nth weekdays are handled.
- When calculating instants, wall-clock times skipped by a daylight-saving gap are skipped.
- When calculating instants, ambiguous wall-clock times fire once at the earlier offset.

## Serialization

`Cron` serializes as its expression string.
Segment order is preserved, so equivalent expressions written in a different order serialize differently.

```kotlin
Json.encodeToString(Cron.serializer(), expression)
// result: "0 */15 9-17 ? * MON-FRI 2026"
```
