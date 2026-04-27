package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.CalculatorSupport
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.statistics.config83.PlatformResourceCategory
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonPrimitive

data class GatewayNetworkStatistics(
    val outgoing: List<OutgoingConnection>,
    val incoming: List<IncomingConnection>,
) : Statistic {
    data class OutgoingConnection(
        val host: String,
        val port: Int,
        val enabled: Boolean,
    )

    data class IncomingConnection(
        val uuid: String,
    )

    companion object Calculators : CalculatorSupport<GatewayNetworkStatistics>(
        mapOf(
            MajorVersion.SevenNine to Calculator,
            MajorVersion.EightZero to Calculator,
            MajorVersion.EightOne to Calculator,
            MajorVersion.EightThree to Calculator83,
        ),
    )

    @Suppress("SqlResolve")
    object Calculator : StatisticCalculator<GatewayNetworkStatistics> {
        private val OUTGOING_CONNECTIONS =
            """
            SELECT
                host,
                port,
                enabled
            FROM
                wsconnectionsettings
            """.trimIndent()

        private val INCOMING_CONNECTIONS =
            """
            SELECT
                connectionid
            FROM
                wsincomingconnection
            """.trimIndent()

        override suspend fun calculate(backup: GatewayBackup): GatewayNetworkStatistics? {
            val outgoing =
                backup.configDb.executeQuery(OUTGOING_CONNECTIONS)
                    .toList { rs ->
                        OutgoingConnection(
                            host = rs[1],
                            port = rs[2],
                            enabled = rs[3],
                        )
                    }

            val incoming =
                backup.configDb.executeQuery(INCOMING_CONNECTIONS)
                    .toList { rs ->
                        IncomingConnection(rs[1])
                    }

            if (outgoing.isEmpty() && incoming.isEmpty()) {
                return null
            }

            return GatewayNetworkStatistics(outgoing, incoming)
        }
    }

    object Calculator83 : StatisticCalculator<GatewayNetworkStatistics> {
        override suspend fun calculate(backup: GatewayBackup): GatewayNetworkStatistics? = coroutineScope {
            val outgoingCategory = backup.resources.getPlatformCategory(
                PlatformResourceCategory.GATEWAY_NETWORK_OUTGOING,
            )
            val incomingCategory = backup.resources.getPlatformCategory(
                PlatformResourceCategory.GATEWAY_NETWORK_INCOMING,
            )

            val outgoing = outgoingCategory.resources.map {
                async {
                    OutgoingConnection(
                        host = it.config["host"]!!.jsonPrimitive.content,
                        port = it.config["port"]!!.jsonPrimitive.content.toInt(),
                        enabled = it.data.attributes.enabled,
                    )
                }
            }.toList().awaitAll()

            val incoming = incomingCategory.resources.map {
                async {
                    IncomingConnection(it.config["connectionId"]!!.jsonPrimitive.content)
                }
            }.toList().awaitAll()

            if (outgoing.isEmpty() && incoming.isEmpty()) return@coroutineScope null
            GatewayNetworkStatistics(outgoing, incoming)
        }
    }
}
