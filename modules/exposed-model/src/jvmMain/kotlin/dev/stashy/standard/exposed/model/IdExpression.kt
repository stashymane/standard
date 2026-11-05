package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException

/**
 * Expression to generate an [Id].
 *
 * A built-in SQL function is used when available:
 * - PostgreSQL 18+ uses `uuidv7()`
 * - MariaDB 11.7+ uses `UUID_v7()`
 * - H2 uses `RANDOM_UUID(7)`
 *
 * When no built-in function is available, [fallback] is asked for a custom expression.
 * Dialects with neither fail when the default is rendered.
 */
public class IdExpression<T>(
    private val fallback: Resolver? = null,
) : Expression<Id<T>>() {
    @OptIn(InternalApi::class)
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        val db = currentTransaction().db
        val function = uuidV7Function(db.dialect, db.version)
            ?: fallback?.resolve(db.dialect, db.version)
            ?: throw UnsupportedByDialectException(
                "This dialect does not provide a UUIDv7 function and no fallback resolver supplied one.",
                db.dialect,
            )

        queryBuilder.append(function)
    }

    public fun interface Resolver {
        public fun resolve(dialect: DatabaseDialect, version: Version): String?
    }
}

private fun uuidV7Function(dialect: DatabaseDialect, version: Version): String? = when (dialect) {
    is PostgreSQLDialect if version.covers(18) -> "uuidv7()"
    is MariaDBDialect if version.covers(11, 7) -> "UUID_v7()"
    is H2Dialect if version.covers(2, 3, 232) -> "RANDOM_UUID(7)"
    else -> null
}
