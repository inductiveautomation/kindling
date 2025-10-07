package io.github.inductiveautomation.kindling.cache.hsql.model

import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serializable
import java.util.Date

@Suppress("unused")
class AuditProfileData(
    private val auditRecord: DefaultAuditRecord,
    private val insertQuery: String,
    private val parentLog: String,
) : Serializable {
    fun toDetail() = Detail(
        title = "Audit Profile Data",
        message = insertQuery,
        body = mapOf(
            "actor" to auditRecord.actor,
            "action" to auditRecord.action,
            "actionValue" to auditRecord.actionValue,
            "actionTarget" to auditRecord.actionTarget,
            "actorHost" to auditRecord.actorHost,
            "originatingContext" to when (auditRecord.originatingContext) {
                1 -> "Gateway"
                2 -> "Designer"
                4 -> "Client"
                else -> "Unknown"
            },
            "originatingSystem" to auditRecord.originatingSystem,
            "timestamp" to auditRecord.timestamp.toString(),
        ).map { (key, value) ->
            "$key: $value"
        },
    )

    companion object {
        @JvmStatic
        private val serialVersionUID = 3037488986978918285L
    }
}

@Suppress("unused")
class DefaultAuditRecord(
    val originatingContext: Int,
    val statusCode: Int,
    val action: String,
    val actionTarget: String?,
    val actionValue: String,
    val actor: String,
    val actorHost: String,
    val originatingSystem: String,
    val timestamp: Date,
) : Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID = 0x21d9a89f09913781
    }
}
