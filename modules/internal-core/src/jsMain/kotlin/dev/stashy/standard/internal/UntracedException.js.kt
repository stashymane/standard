package dev.stashy.standard.internal

private var captureStackGuarded = false

/**
 * Kotlin/JS inserts a stack capture into every Throwable subclass constructor.
 * Guard the runtime hook so subclasses created through the base constructor skip capture entirely.
 * JS Engines without Error.captureStackTrace are left untouched rather than clearing a stack after it was built.
 */
private fun installCaptureStackGuard() {
    if (captureStackGuarded) return
    captureStackGuarded = true

    val error = js("Error")
    val original = error.captureStackTrace
    if (original != null) {
        error.captureStackTrace = { target: dynamic, constructor: dynamic ->
            if (target is UntracedException) {
                target.stack = null
            } else {
                original(target, constructor)
            }
        }
    }
}

@SubclassOptInRequired(InternalStandardApi::class)
public actual abstract class UntracedException actual constructor(message: String?) : Throwable(message) {
    init {
        installCaptureStackGuard()
    }
}
