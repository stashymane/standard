package dev.stashy.worker.internal

internal class InvalidHandlerPayloadException(cause: Throwable) : Exception(cause.message, cause)
