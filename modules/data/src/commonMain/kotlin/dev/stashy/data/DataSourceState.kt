package dev.stashy.data

/**
 * The last completed state of a [DataSource].
 */
public sealed interface DataSourceState<out T : Any> {
    /** No value is available yet and a load has not completed. */
    public data object Loading : DataSourceState<Nothing>

    /** A value is available. */
    public data class Success<T : Any>(
        public val value: T,
    ) : DataSourceState<T>

    /** The last load failed. */
    public data class Failure<out T : Any>(
        public val cause: Throwable,
    ) : DataSourceState<T>
}

/** Returns the successfully loaded value, or null if none is available. */
public val <T : Any> DataSourceState<T>.valueOrNull: T?
    get() = (this as? DataSourceState.Success)?.value

internal fun <T : Any, R : Any> DataSourceState<T>.mapState(
    transform: (T) -> R,
): DataSourceState<R> = when (this) {
    DataSourceState.Loading -> DataSourceState.Loading
    is DataSourceState.Success -> runCatching { transform(value) }.fold(
        onSuccess = { DataSourceState.Success(it) },
        onFailure = { DataSourceState.Failure(it) },
    )

    is DataSourceState.Failure -> DataSourceState.Failure(cause)
}

internal fun <T : Any> Result<T>.toDataSourceState(): DataSourceState<T> =
    if (isSuccess) {
        DataSourceState.Success(getOrThrow())
    } else {
        DataSourceState.Failure(checkNotNull(exceptionOrNull()))
    }
