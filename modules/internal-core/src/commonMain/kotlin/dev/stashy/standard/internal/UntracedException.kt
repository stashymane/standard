package dev.stashy.standard.internal

/**
 * Signals that execution should return early without collecting a stack trace.
 *
 * When using this exception, only catch your own subclass of it, as otherwise you risk interfering with other uses of it.
 * This exception uses wacky workarounds and dangerous hacks, so keep usage of it very conservative.
 *
 * Inspired by `kotlinx.coroutines` [JobCancellationException](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/Exceptions.kt#L40)
 * and Arrow's [`NoTrace`](https://github.com/arrow-kt/arrow/blob/e2a31808c4d4173ed7fdcc84acfae1ffd1505761/arrow-libs/core/arrow-core/src/androidAndJvmMain/kotlin/arrow/core/raise/CancellationExceptionNoTrace.kt#L18).
 */
@SubclassOptInRequired(InternalStandardApi::class)
public expect abstract class UntracedException(message: String? = null) : Throwable
