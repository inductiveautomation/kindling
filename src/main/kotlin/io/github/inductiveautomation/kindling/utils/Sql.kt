package io.github.inductiveautomation.kindling.utils

import org.intellij.lang.annotations.Language
import org.sqlite.SQLiteDataSource
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.Date
import java.sql.JDBCType
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp

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

val JDBCType.javaType: Class<*>
    get() =
        when (this) {
            JDBCType.BIT -> Boolean::class
            JDBCType.TINYINT -> Short::class
            JDBCType.SMALLINT -> Short::class
            JDBCType.INTEGER -> Int::class
            JDBCType.BIGINT -> Long::class
            JDBCType.FLOAT -> Float::class
            JDBCType.REAL -> Double::class
            JDBCType.DOUBLE -> Double::class
            JDBCType.NUMERIC -> BigDecimal::class
            JDBCType.DECIMAL -> BigDecimal::class
            JDBCType.CHAR -> String::class
            JDBCType.VARCHAR -> String::class
            JDBCType.LONGVARCHAR -> String::class
            JDBCType.DATE -> Date::class
            JDBCType.TIME -> Time::class
            JDBCType.TIMESTAMP -> Timestamp::class
            JDBCType.BINARY -> ByteArray::class
            JDBCType.VARBINARY -> ByteArray::class
            JDBCType.LONGVARBINARY -> ByteArray::class
            JDBCType.BOOLEAN -> Boolean::class
            JDBCType.ROWID -> Long::class
            JDBCType.NCHAR -> String::class
            JDBCType.NVARCHAR -> String::class
            JDBCType.LONGNVARCHAR -> String::class
            JDBCType.BLOB -> ByteArray::class
            JDBCType.CLOB -> ByteArray::class
            JDBCType.NCLOB -> ByteArray::class
            else -> Any::class
        }.javaObjectType
