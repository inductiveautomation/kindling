package io.github.inductiveautomation.kindling.cache.hsql.model

import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serializable

@Suppress("unused")
class ScriptedSFData(
    val query: String,
    val datasource: String,
    val values: Array<Any?>,
) : Serializable {
    fun toDetail() = Detail(
        title = "system.db.runSFUpdate query data",
        message = query,
        details = mapOf(
            "datasource" to datasource,
        ),
        body = values.mapIndexed { index, parameterValue ->
            "param${index + 1} (${parameterValue?.javaClass?.simpleName}) = $parameterValue"
        },
    )

    companion object {
        @JvmStatic
        private val serialVersionUID = 1L
    }
}
