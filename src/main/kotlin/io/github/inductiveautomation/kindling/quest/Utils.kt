package io.github.inductiveautomation.kindling.quest

import io.questdb.cairo.CairoEngine
import io.questdb.cairo.ColumnType
import io.questdb.cairo.sql.Record
import io.questdb.cairo.sql.RecordMetadata
import io.questdb.griffin.SqlExecutionContext
import java.util.Date
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

context(sqlExec: SqlExecutionContext)
internal fun <T> CairoEngine.select(
    query: String,
    transform: context(RecordMetadata) (Record) -> T,
): List<T> = buildList {
    select(query, sqlExec).use { stmt ->
        stmt.getCursor(sqlExec).use { cursor ->
            while (cursor.hasNext()) {
                add(transform(stmt.metadata, cursor.record))
            }
        }
    }
}

context(meta: RecordMetadata)
internal inline operator fun <reified T> Record.get(name: String): T? {
    return get(meta.getColumnIndex(name))
}

@OptIn(ExperimentalUuidApi::class)
context(meta: RecordMetadata)
internal inline operator fun <reified T> Record.get(index: Int): T? {
    val type = meta.getColumnType(index)
    val clazz = meta.getColumnClass(index)

    if (T::class != Any::class && T::class != clazz) return null

    return when (type.toShort()) {
        ColumnType.SYMBOL -> getSymA(index)?.toString()
        ColumnType.STRING -> getStrA(index)?.toString()
        ColumnType.VARCHAR -> getVarcharA(index)?.toString()
        ColumnType.BYTE -> getByte(index)
        ColumnType.SHORT -> getShort(index)
        ColumnType.INT -> getInt(index)
        ColumnType.LONG -> getLong(index)
        ColumnType.FLOAT -> getFloat(index)
        ColumnType.DOUBLE -> getDouble(index)
        ColumnType.CHAR -> getChar(index)
        ColumnType.BOOLEAN -> getBool(index)
        ColumnType.TIMESTAMP -> getTimestamp(index).takeIf { it >= 0 }?.let {
            Date(it / 1000)
        }
        ColumnType.UUID -> Uuid.parse(getStrA(index).toString())
        ColumnType.BINARY -> getBin(index)?.let { seq ->
            ByteArray(seq.length().toInt()) { i ->
                seq.byteAt(i.toLong())
            }
        }

        else -> error("Unable to parse column type: ${ColumnType.nameOf(type)}")
    } as T
}

@OptIn(ExperimentalUuidApi::class)
internal fun RecordMetadata.getColumnClass(index: Int): KClass<*>? {
    val type = getColumnType(index)
    return when (type.toShort()) {
        ColumnType.SYMBOL,
        ColumnType.STRING,
        ColumnType.VARCHAR,
        -> String::class

        ColumnType.BYTE -> Byte::class
        ColumnType.SHORT -> Short::class
        ColumnType.INT -> Int::class
        ColumnType.LONG -> Long::class
        ColumnType.TIMESTAMP -> Date::class
        ColumnType.FLOAT -> Float::class
        ColumnType.DOUBLE -> Double::class
        ColumnType.CHAR -> Char::class
        ColumnType.BOOLEAN -> Boolean::class
        ColumnType.BINARY -> ByteArray::class
        ColumnType.UUID -> Uuid::class
        else -> error("Unknown column type: ${ColumnType.nameOf(type)}")
    }
}
