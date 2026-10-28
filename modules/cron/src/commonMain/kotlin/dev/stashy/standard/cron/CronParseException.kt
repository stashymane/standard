package dev.stashy.standard.cron

/**
 * Thrown when a cron expression cannot be parsed.
 */
public class CronParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
