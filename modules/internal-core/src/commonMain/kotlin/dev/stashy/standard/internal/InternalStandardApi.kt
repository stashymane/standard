package dev.stashy.standard.internal

@RequiresOptIn(
    message = "This API is internal and usage of it is discouraged."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class InternalStandardApi
