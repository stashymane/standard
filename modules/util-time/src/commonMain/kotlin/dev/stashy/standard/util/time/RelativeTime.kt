package dev.stashy.standard.util.time

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Formats this instant as a short relative time string (e.g. `"5m ago"`, `"in 5m"`),
 * using Compose Multiplatform string resources for localization.
 */
@Composable
public fun Instant.toRelativeString(now: Instant = Clock.System.now()): String {
    val elapsed = now - this
    return if (elapsed.isNegative()) {
        formatRelative(this - now, past = false)
    } else {
        formatRelative(elapsed, past = true)
    }
}

@Composable
private fun formatRelative(amount: Duration, past: Boolean): String = when {
    amount < 1.minutes -> stringResource(Res.string.time_just_now)
    amount < 1.hours -> stringResource(
        if (past) Res.string.time_minutes_ago else Res.string.time_minutes_in,
        amount.inWholeMinutes,
    )
    amount < 1.days -> stringResource(
        if (past) Res.string.time_hours_ago else Res.string.time_hours_in,
        amount.inWholeHours,
    )
    amount < 30.days -> stringResource(
        if (past) Res.string.time_days_ago else Res.string.time_days_in,
        amount.inWholeDays,
    )
    amount < 365.days -> stringResource(
        if (past) Res.string.time_months_ago else Res.string.time_months_in,
        amount.inWholeDays / 30,
    )
    else -> stringResource(
        if (past) Res.string.time_years_ago else Res.string.time_years_in,
        amount.inWholeDays / 365,
    )
}
