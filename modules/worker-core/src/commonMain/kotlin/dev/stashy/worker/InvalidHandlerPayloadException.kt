package dev.stashy.worker

internal class InvalidHandlerPayloadException(cause: Throwable) : Exception(cause.message, cause)
