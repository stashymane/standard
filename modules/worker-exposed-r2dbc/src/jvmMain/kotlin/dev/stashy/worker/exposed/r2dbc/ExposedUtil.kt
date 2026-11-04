package dev.stashy.worker.exposed.r2dbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

internal suspend inline fun <T> R2dbcDatabase.transaction(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline block: suspend R2dbcTransaction.() -> T
): T = withContext(dispatcher) {
    suspendTransaction(db = this@transaction) { block() }
}
