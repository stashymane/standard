package dev.stashy.standard.ui.components

import androidx.compose.runtime.*
import dev.stashy.standard.util.time.RelativeTime
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Formats this instant as a short relative time string (e.g. `"5s ago"`, `"in 1h 20m"`),
 * using Compose Multiplatform string resources for localization.
 *
 * The string updates automatically when its displayed value changes.
 *
 * @param maxComponents The maximum number of units to display.
 */
@Composable
public fun Instant.toRelativeString(
    clock: Clock = Clock.System,
    maxComponents: Int = 2,
): String {
    val instant = this
    var relative by remember(instant, clock, maxComponents) {
        mutableStateOf(RelativeTime.of(instant, clock.now(), maxComponents))
    }

    LaunchedEffect(instant, clock, maxComponents) {
        while (true) {
            val now = clock.now()
            relative = RelativeTime.of(instant, now, maxComponents)
            delay(relative.millisUntilNextChange(instant, now).milliseconds)
        }
    }

    return relative.localized()
}

@Composable
private fun RelativeTime.localized(): String {
    if (isJustNow) return stringResource(Res.string.time_just_now)

    val parts = mutableListOf<String>()
    for (component in components) {
        parts += component.localized()
    }

    var body = parts.first()
    for (index in 1 until parts.size) {
        body = stringResource(Res.string.time_component_join, body, parts[index])
    }

    return stringResource(if (past) Res.string.time_ago else Res.string.time_in, body)
}

@Composable
private fun RelativeTime.Component.localized(): String = when (unit) {
    RelativeTime.Units.Seconds -> stringResource(Res.string.time_seconds, amount)
    RelativeTime.Units.Minutes -> stringResource(Res.string.time_minutes, amount)
    RelativeTime.Units.Hours -> stringResource(Res.string.time_hours, amount)
    RelativeTime.Units.Days -> stringResource(Res.string.time_days, amount)
    RelativeTime.Units.Months -> stringResource(Res.string.time_months, amount)
    RelativeTime.Units.Years -> stringResource(Res.string.time_years, amount)
}
