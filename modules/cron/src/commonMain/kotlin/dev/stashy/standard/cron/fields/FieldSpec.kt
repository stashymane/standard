package dev.stashy.standard.cron.fields

internal class FieldSpec<T : CronField>(
    internal val bounds: IntRange,
    private val default: List<Segment> = listOf(Segment(bounds.first, bounds.last)),
    private val create: (List<Segment>) -> T,
) {
    private val segments = mutableListOf<Segment>()

    internal var touched: Boolean = false
        private set

    internal fun add(value: Int) {
        require(value in bounds) { "Value $value is outside ${bounds.first}..${bounds.last}" }
        touch()
        segments += Segment(value, value)
    }

    internal fun addRange(from: Int, to: Int, step: Int = 1) {
        require(step > 0) { "Step must be greater than zero" }
        require(from in bounds && to in bounds) {
            "Range $from..$to is outside ${bounds.first}..${bounds.last}"
        }
        require(from <= to) { "Range start $from must not be greater than end $to" }
        touch()
        segments += Segment(from, to, step)
    }

    internal fun snapshot(): T =
        create(if (touched) segments.toList() else default)

    internal fun snapshotOrNull(): T? =
        if (touched) create(segments.toList()) else null

    private fun touch() {
        if (!touched) {
            touched = true
            segments.clear()
        }
    }
}
