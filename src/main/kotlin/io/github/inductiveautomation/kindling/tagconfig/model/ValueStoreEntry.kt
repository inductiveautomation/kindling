package io.github.inductiveautomation.kindling.tagconfig.model

data class ValueStoreEntry(
    val provider: String,
    val path: String,
    val dataType: Int,
    val textValue: String?,
    val numericValue: Any?,
    val nullValue: Int,
    val quality: Int,
    val t_stamp: Long,
    val updatedAt: Long,
)

// TODO: Add support for all tag data types. Figure out how complex tags are encoded.

enum class ValueStoreDataType(val dbValue: Int) {
    BYTE(0),
    SHORT(1),
    INTEGER(2),
    LONG(3),
    FLOAT(4),
    DOUBLE(5),
    BOOLEAN(6),
    STRING(7),
    DATETIME(8),
    ;

    companion object {
        private val entriesById = ValueStoreDataType.entries.associateBy { it.dbValue }
        fun fromDbValue(dbValue: Int) = entriesById[dbValue]
    }
}
