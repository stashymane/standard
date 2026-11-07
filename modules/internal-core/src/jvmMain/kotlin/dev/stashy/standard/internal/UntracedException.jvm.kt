package dev.stashy.standard.internal

@SubclassOptInRequired(InternalStandardApi::class)
public actual abstract class UntracedException actual constructor(message: String?) : Throwable(message) {
    override fun fillInStackTrace(): Throwable {
        stackTrace = emptyArray()
        return this
    }
}
