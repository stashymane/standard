package dev.stashy.standard.cron.fields

internal data class Segment(
    val from: Int,
    val to: Int,
    val step: Int = 1,
) {
    init {
        require(step > 0) { "Step must be greater than zero" }
        require(from <= to) { "Segment start $from must not be greater than end $to" }
    }

    internal operator fun contains(value: Int): Boolean =
        value in from..to && (value - from) % step == 0

    internal fun nextOrNull(after: Int): Int? {
        if (after >= to) return null

        val start = maxOf(after + 1, from)
        val offset = (start - from) % step
        val next = if (offset == 0) start.toLong() else start.toLong() + (step - offset)
        return if (next <= to) next.toInt() else null
    }
}
