package dev.stashy.worker.handler

/**
 * How a job left the queue.
 *
 * A recurring job only finalizes through [Cancelled], because a failed occurrence advances to its
 * next one.
 */
public sealed interface TerminalOutcome {
    /**
     * A one-shot job completed successfully.
     */
    public data object Succeeded : TerminalOutcome

    /**
     * The job failed permanently, for example because its attempts were exhausted, or it started too
     * late.
     */
    public data class Failed(public val message: String) : TerminalOutcome

    /**
     * The job was cancelled before or during execution.
     */
    public data object Cancelled : TerminalOutcome
}
