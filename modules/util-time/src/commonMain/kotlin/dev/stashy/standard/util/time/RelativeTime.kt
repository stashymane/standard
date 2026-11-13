package dev.stashy.standard.util.time

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal data class RelativeTime(
    val unit: Unit,
    val amount: Long,
    val past: Boolean,
) {
    enum class Unit {
        JustNow,
        Seconds,
        Minutes,
        Hours,
        Days,
        Months,
        Years,
    }

    companion object {
        fun of(instant: Instant, now: Instant): RelativeTime {
            val elapsed = now - instant
            val amount = elapsed.absoluteValue
            val past = !elapsed.isNegative()
            return when {
                amount < 1.seconds -> RelativeTime(Unit.JustNow, 0, past = true)
                amount < 1.minutes -> RelativeTime(Unit.Seconds, amount.inWholeSeconds, past)
                amount < 1.hours -> RelativeTime(Unit.Minutes, amount.inWholeMinutes, past)
                amount < 1.days -> RelativeTime(Unit.Hours, amount.inWholeHours, past)
                amount < 30.days -> RelativeTime(Unit.Days, amount.inWholeDays, past)
                amount < 365.days -> RelativeTime(Unit.Months, amount.inWholeDays / 30, past)
                else -> RelativeTime(Unit.Years, amount.inWholeDays / 365, past)
            }
        }
    }

    fun millisUntilNextChange(instant: Instant, now: Instant): Long {
        val elapsed = now - instant
        if (unit == Unit.JustNow) {
            return if (elapsed.isNegative()) {
                (-elapsed).inWholeMilliseconds.coerceAtLeast(1)
            } else {
                (1.seconds - elapsed).inWholeMilliseconds.coerceAtLeast(1)
            }
        }

        val width = unit.width.inWholeMilliseconds
        val amount = elapsed.absoluteValue.inWholeMilliseconds
        val remainder = amount % width
        val millis = if (past) {
            val untilNextValue = width - remainder
            val untilNextUnit = unit.end?.let { it.inWholeMilliseconds - amount } ?: Long.MAX_VALUE
            minOf(untilNextValue, untilNextUnit)
        } else {
            remainder + 1
        }
        return millis.coerceAtLeast(1)
    }
}

/**
 * Formats this instant as a short relative time string (e.g. `"5s ago"`, `"in 5m"`),
 * using Compose Multiplatform string resources for localization.
 *
 * The string updates automatically when its displayed value changes.
 */
@Composable
public fun Instant.toRelativeString(clock: Clock = Clock.System): String {
    val instant = this
    var relative by remember(instant, clock) {
        mutableStateOf(RelativeTime.of(instant, clock.now()))
    }

    LaunchedEffect(instant, clock) {
        while (true) {
            val now = clock.now()
            relative = RelativeTime.of(instant, now)
            delay(relative.millisUntilNextChange(instant, now).milliseconds)
        }
    }

    return relative.localized()
}

@Composable
private fun RelativeTime.localized(): String = when (unit) {
    RelativeTime.Unit.JustNow -> stringResource(Res.string.time_just_now)
    RelativeTime.Unit.Seconds -> stringResource(
        if (past) Res.string.time_seconds_ago else Res.string.time_seconds_in,
        amount,
    )
    RelativeTime.Unit.Minutes -> stringResource(
        if (past) Res.string.time_minutes_ago else Res.string.time_minutes_in,
        amount,
    )

    RelativeTime.Unit.Hours -> stringResource(
        if (past) Res.string.time_hours_ago else Res.string.time_hours_in,
        amount,
    )

    RelativeTime.Unit.Days -> stringResource(
        if (past) Res.string.time_days_ago else Res.string.time_days_in,
        amount,
    )

    RelativeTime.Unit.Months -> stringResource(
        if (past) Res.string.time_months_ago else Res.string.time_months_in,
        amount,
    )

    RelativeTime.Unit.Years -> stringResource(
        if (past) Res.string.time_years_ago else Res.string.time_years_in,
        amount,
    )
}

private val RelativeTime.Unit.width: Duration
    get() = when (this) {
        RelativeTime.Unit.JustNow,
        RelativeTime.Unit.Seconds -> 1.seconds
        RelativeTime.Unit.Minutes -> 1.minutes

        RelativeTime.Unit.Hours -> 1.hours
        RelativeTime.Unit.Days -> 1.days
        RelativeTime.Unit.Months -> 30.days
        RelativeTime.Unit.Years -> 365.days
    }

private val RelativeTime.Unit.end: Duration?
    get() = when (this) {
        RelativeTime.Unit.JustNow -> 1.seconds
        RelativeTime.Unit.Seconds -> 1.minutes
        RelativeTime.Unit.Minutes -> 1.hours

        RelativeTime.Unit.Hours -> 1.days
        RelativeTime.Unit.Days -> 30.days
        RelativeTime.Unit.Months -> 365.days
        RelativeTime.Unit.Years -> null
    }
