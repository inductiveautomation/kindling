package io.github.inductiveautomation.kindling.utils

import org.intellij.lang.annotations.Language
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet

/**
 * Exhausts (and closes) a ResultSet into a list using [transform].
 */
fun <T> ResultSet.toList(transform: (ResultSet) -> T): List<T> {
    return use { rs ->
        buildList {
            while (rs.next()) {
                add(transform(rs))
            }
        }
    }
}

/**
 * Exhausts (and closes) a ResultSet into a mutable mapping builder (via [rowConsumer]), returning an immutable map.
 */
fun <K, V> ResultSet.toMap(rowConsumer: MutableMap<K, V>.(ResultSet) -> Unit): Map<K, V> {
    return use { rs ->
        buildMap {
            while (rs.next()) {
                rowConsumer(rs)
            }
        }
    }
}

/**
 * Closes a ResultSet, creating a map of each column to each value from the first returned row.
 */
fun ResultSet.asScalarMap(): Map<String, Any?> {
    return use { rs ->
        buildMap {
            repeat(rs.metaData.columnCount) { i ->
                put(rs.metaData.getColumnName(i + 1), rs[i + 1])
            }
        }
    }
}

/**
 * Immediately creates a statement and executes the given query.
 * Should not be used if a query will be called multiple times.
 *
 * @return a new, open [ResultSet].
 */
fun Connection.executeQuery(
    @Language("sql") sql: String,
): ResultSet {
    return createStatement().executeQuery(sql)
}

inline operator fun <reified T> ResultSet.get(column: Int): T {
    return sqliteCoercion(getObject(column))
}

inline operator fun <reified T> ResultSet.get(column: String): T {
    return sqliteCoercion(getObject(column))
}

inline fun <reified T> sqliteCoercion(raw: Any?): T {
    return when {
        raw is Int && T::class == Long::class -> raw.toLong()
        raw is Int && T::class == Boolean::class -> raw == 1
        else -> raw
    } as T
}

@Suppress("FunctionName")
fun SQLiteConnection(
    path: Path,
    readOnly: Boolean = true,
): Connection {
    return SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(readOnly)
        setJournalMode("OFF")
        setSynchronous("OFF")
    }.connection
}
