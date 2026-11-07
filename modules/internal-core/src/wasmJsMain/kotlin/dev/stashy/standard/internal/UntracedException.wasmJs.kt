@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.stashy.standard.internal

/**
 * Wasm's public Throwable constructors always create a JS Error with a captured stack.
 * The internal constructor lets us provide an Error-like prototype object instead, so no Error
 * constructor runs and no stack is ever collected.
 *
 * This approach is fragile and bad and should be replaced as soon as something better is possible.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    () => {
        const error = Object.create(Error.prototype);
        error.message = '';
        error.name = '';
        error.stack = '';
        error.cause = null;
        error.kotlinException = null;
        return error;
    }
    """,
)
private external fun emptyJsError(): JsAny

@OptIn(ExperimentalWasmJsInterop::class)
@SubclassOptInRequired(InternalStandardApi::class)
public actual abstract class UntracedException actual constructor(message: String?) :
    Throwable(message, null, emptyJsError().unsafeCast<JsError>())
