package dev.stashy.standard.benchmarks

import dev.stashy.standard.internal.InternalStandardApi
import dev.stashy.standard.internal.UntracedException
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@OptIn(InternalStandardApi::class)
private class BenchmarkUntracedException : UntracedException()

@State(Scope.Benchmark)
open class ExceptionBenchmark {
    private val input: Int = 1
    private val sharedUntraced: BenchmarkUntracedException = BenchmarkUntracedException()

    @Benchmark
    open fun baseline(): Int = input + 1

    @Benchmark
    open fun regularException(): Int =
        try {
            baseline()
            throw IllegalStateException("failure")
        } catch (exception: IllegalStateException) {
            exception.message.orEmpty().length
        }

    @Benchmark
    open fun untracedException(): Int =
        try {
            baseline()
            throw BenchmarkUntracedException()
        } catch (_: BenchmarkUntracedException) {
            0
        }

    @Benchmark
    open fun sharedUntracedException(): Int =
        try {
            baseline()
            throw sharedUntraced
        } catch (_: BenchmarkUntracedException) {
            0
        }
}
