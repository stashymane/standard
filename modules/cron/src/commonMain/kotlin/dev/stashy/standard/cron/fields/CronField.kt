package dev.stashy.standard.cron.fields

import dev.stashy.standard.cron.CronParseException

internal sealed class CronField {
    abstract val bounds: IntRange
    internal abstract val segments: List<Segment>

    protected open fun label(value: Int): String = value.toString()

    internal val isFull: Boolean
        get() = segments.any {
            it.from == bounds.first && it.to == bounds.last && it.step == 1
        }

    internal operator fun contains(value: Int): Boolean =
        segments.any { value in it }

    internal fun nextOrNull(after: Int): Int? {
        var next: Int? = null
        for (segment in segments) {
            val candidate = segment.nextOrNull(after) ?: continue
            val current = next
            if (current == null || candidate < current) next = candidate
        }
        return next
    }

    internal fun toExpression(): String {
        if (segments.size == 1) {
            val segment = segments[0]
            if (segment.from == bounds.first && segment.to == bounds.last) {
                return if (segment.step == 1) "*" else "*/${segment.step}"
            }
        }

        return segments.joinToString(",") { render(it) }
    }

    private fun render(segment: Segment): String = when {
        segment.from == segment.to -> label(segment.from)
        segment.step == 1 -> "${label(segment.from)}-${label(segment.to)}"
        segment.from == bounds.first && segment.to == bounds.last -> "*/${segment.step}"
        else -> "${label(segment.from)}-${label(segment.to)}/${segment.step}"
    }

    internal data class Seconds(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        internal companion object {
            internal val BOUNDS = 0..59

            internal fun parse(field: String): Seconds =
                parseField(field, BOUNDS, "seconds") { Seconds(it) }
        }
    }

    internal data class Minutes(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        internal companion object {
            internal val BOUNDS = 0..59

            internal fun parse(field: String): Minutes =
                parseField(field, BOUNDS, "minutes") { Minutes(it) }
        }
    }

    internal data class Hours(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        internal companion object {
            internal val BOUNDS = 0..23

            internal fun parse(field: String): Hours =
                parseField(field, BOUNDS, "hours") { Hours(it) }
        }
    }

    internal data class DaysOfMonth(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        internal companion object {
            internal val BOUNDS = 1..31

            internal fun parse(field: String): DaysOfMonth =
                parseField(field, BOUNDS, "day-of-month") { DaysOfMonth(it) }
        }
    }

    internal data class Months(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        override fun label(value: Int): String = LABELS[value - 1]

        internal companion object {
            private val LABELS = listOf(
                "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
            )
            private val NAMES = LABELS.withIndex()
                .associate { (index, name) -> name to index + 1 }

            internal val BOUNDS = 1..12

            internal fun parse(field: String): Months =
                parseField(field, BOUNDS, "month", NAMES) { Months(it) }
        }
    }

    internal data class DaysOfWeek(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        override fun label(value: Int): String = LABELS[value - 1]

        internal companion object {
            private val LABELS = listOf(
                "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT",
            )
            private val NAMES = LABELS.withIndex()
                .associate { (index, name) -> name to index + 1 }

            internal val BOUNDS = 1..7

            internal fun parse(field: String): DaysOfWeek =
                parseField(field, BOUNDS, "day-of-week", NAMES) { DaysOfWeek(it) }

            internal fun parseValue(token: String): Int =
                parseValue(token, BOUNDS, "day-of-week", NAMES)

            internal fun labelFor(value: Int): String = LABELS[value - 1]
        }
    }

    internal data class Years(override val segments: List<Segment>) : CronField() {
        override val bounds: IntRange get() = BOUNDS

        init {
            validateSegments(segments, BOUNDS)
        }

        internal companion object {
            // Matches the LocalDate year range provided by kotlinx-datetime.
            internal const val MIN_YEAR = -999_999_999
            internal const val MAX_YEAR = 999_999_999
            internal val BOUNDS = MIN_YEAR..MAX_YEAR

            internal fun parse(field: String): Years =
                parseField(field, BOUNDS, "year") { Years(it) }
        }
    }
}

private fun validateSegments(segments: List<Segment>, bounds: IntRange) {
    require(segments.isNotEmpty()) { "A cron field cannot be empty" }
    for ((from, to) in segments) {
        require(from >= bounds.first && to <= bounds.last) {
            "Cron field values must be within ${bounds.first}..${bounds.last}"
        }
    }
}

private fun <T : CronField> parseField(
    field: String,
    bounds: IntRange,
    name: String,
    names: Map<String, Int> = emptyMap(),
    create: (List<Segment>) -> T,
): T {
    val segments = field.split(',').map { item ->
        if (item.isEmpty())
            throw CronParseException("Empty $name value in '$field'")

        parseItem(item, bounds, name, names)
    }

    if (segments.isEmpty())
        throw CronParseException("Empty $name field")
    return create(segments)
}

private fun parseValue(
    token: String,
    bounds: IntRange,
    name: String,
    names: Map<String, Int> = emptyMap(),
): Int {
    val value = names[token.uppercase()] ?: token.toIntOrNull()
    return when {
        token.isEmpty() -> throw CronParseException("Empty $name value")
        value == null -> throw CronParseException("Invalid $name value '$token'")
        value !in bounds -> throw CronParseException(
            "$name value $value is outside ${bounds.first}..${bounds.last}",
        )

        else -> value
    }
}

private fun parseItem(
    item: String,
    bounds: IntRange,
    name: String,
    names: Map<String, Int>,
): Segment {
    val parts = item.split('/')

    val step = when (parts.size) {
        1 -> 1
        2 -> parts[1].toIntOrNull()?.takeIf { it > 0 }
            ?: throw CronParseException("Step must be a positive integer: '$item'")

        else -> throw CronParseException("Invalid $name item '$item'")
    }

    val base = parts[0]
    val (from, end) = when {
        base == "?" -> throw CronParseException("'?' is only valid in day-of-month and day-of-week")
        base == "*" -> bounds.first to bounds.last

        '-' in base -> {
            val rangeParts = base.split('-')
            if (rangeParts.size != 2) {
                throw CronParseException("Invalid $name range '$item'")
            }
            val rangeStart = parseValue(rangeParts[0], bounds, name, names)
            val rangeEnd = parseValue(rangeParts[1], bounds, name, names)
            if (rangeStart > rangeEnd) {
                throw CronParseException("$name range must be ascending: '$item'")
            }
            rangeStart to rangeEnd
        }

        else -> {
            val value = parseValue(base, bounds, name, names)
            if (parts.size == 1) value to value else value to bounds.last
        }
    }

    return Segment(from, end, step)
}
