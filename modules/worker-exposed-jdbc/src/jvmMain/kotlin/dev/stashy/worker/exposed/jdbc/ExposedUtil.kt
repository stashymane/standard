package dev.stashy.worker.exposed.jdbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

internal suspend inline fun <T> Database.transaction(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline block: suspend JdbcTransaction.() -> T
): T = withContext(dispatcher) {
    suspendTransaction(db = this@transaction) { block() }
}
