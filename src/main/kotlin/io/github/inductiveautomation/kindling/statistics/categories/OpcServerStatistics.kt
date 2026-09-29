package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.resources.ResourceType
import io.github.inductiveautomation.kindling.resources.ResourceType.Companion.PLATFORM_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Filesystem
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.InternalDatabase
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.intellij.lang.annotations.Language
import java.sql.ResultSet
import java.sql.SQLException

data class OpcServerStatistics(
    val servers: List<OpcServer>,
) : Statistic {
    data class OpcServer(
        val name: String,
        val type: String,
        val description: String?,
        val readOnly: Boolean,
        val enabled: Boolean?,
    )

    val uaServers = servers.count { it.type == UA_SERVER_TYPE }
    val comServers = servers.count { it.type == COM_SERVER_TYPE }

    @Suppress("SqlResolve")
    companion object Calculator : StatisticCalculator<OpcServerStatistics> {
        const val UA_SERVER_TYPE = "com.inductiveautomation.OpcUaServerType"
        const val COM_SERVER_TYPE = "OPC_COM_ServerType"

        private val UA_SERVER_QUERY =
            """
            SELECT
                o.name,
                o.type,
                o.description,
                o.readonly,
                u.enabled
            FROM
                opcservers o
                JOIN
                    opcuaconnectionsettings u ON o.opcservers_id = u.serversettingsid
            WHERE
                o.type = '$UA_SERVER_TYPE';
            """.trimIndent()

        private val COM_SERVER_QUERY =
            """
            SELECT
                o.name,
                o.type,
                o.description,
                o.readonly,
                c.enabled
            FROM
                opcservers o
                JOIN
                    comserversettingsrecord c ON o.opcservers_id = c.serversettingsid
            WHERE
                o.type = '$COM_SERVER_TYPE';
            """.trimIndent()

        private val OTHER_SERVER_QUERY =
            """
            SELECT
                o.name,
                o.type,
                o.description,
                o.readonly
            FROM
                opcservers o
            WHERE
                o.type NOT IN ('$COM_SERVER_TYPE', '$UA_SERVER_TYPE');
            """.trimIndent()

        override suspend fun calculate(backup: InternalDatabase): OpcServerStatistics? {
            val uaServers = queryServers(backup, UA_SERVER_QUERY, enabled = { it["enabled"] })
            val comServers = queryServers(backup, COM_SERVER_QUERY, enabled = { it["enabled"] })
            val otherServers = queryServers(backup, OTHER_SERVER_QUERY, enabled = { null })

            if (uaServers.isEmpty() && comServers.isEmpty() && otherServers.isEmpty()) {
                return null
            }

            return OpcServerStatistics(uaServers + comServers + otherServers)
        }

        private fun queryServers(
            backup: InternalDatabase,
            @Language("sql")
            query: String,
            enabled: (ResultSet) -> Boolean?,
        ): List<OpcServer> = try {
            backup.configDb
                .executeQuery(query)
                .toList { rs ->
                    OpcServer(
                        name = rs[1],
                        type = rs[2],
                        description = rs[3],
                        readOnly = rs[4],
                        enabled = enabled(rs),
                    )
                }
        } catch (_: SQLException) {
            emptyList()
        }

        private val OPC_CONNECTION = ResourceType(PLATFORM_MODULE_ID, "opc-connection")

        override suspend fun calculate(backup: Filesystem): OpcServerStatistics? {
            val servers = backup.core.resourcesOfType(OPC_CONNECTION).map { resource ->
                val profile = resource.config.getValue("profile").jsonObject
                OpcServer(
                    name = checkNotNull(resource.name),
                    type = profile.getValue("type").jsonPrimitive.content,
                    description = resource.description,
                    readOnly = profile["readOnly"]?.jsonPrimitive?.boolean ?: false,
                    enabled = resource.enabled,
                )
            }

            if (servers.isEmpty()) {
                return null
            }

            return OpcServerStatistics(servers)
        }
    }
}
