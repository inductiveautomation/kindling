package io.github.inductiveautomation.kindling.cache.hsql

data class SchemaRecord(
    val id: Int,
    val name: String,
    val errors: List<String>,
)

data class SchemaRow(
    val id: Int,
    val signature: String,
    val message: String?,
)
