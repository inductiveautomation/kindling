package io.github.inductiveautomation.kindling.cache.model

import com.inductiveautomation.ignition.gateway.audit.AuditRecord
import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serializable

@Suppress("unused")
class AuditProfileData(
    private val auditRecord: AuditRecord,
    private val insertQuery: String,
    private val parentLog: String,
) : Serializable {
    fun toDetail() =
        Detail(
            title = "Audit Profile Data",
            message = insertQuery,
            body =
                mapOf(
                    "actor" to auditRecord.actor,
                    "action" to auditRecord.action,
                    "actionValue" to auditRecord.actionValue,
                    "actionTarget" to auditRecord.actionTarget,
                    "actorHost" to auditRecord.actorHost,
                    "originatingContext" to
                        when (auditRecord.originatingContext) {
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
