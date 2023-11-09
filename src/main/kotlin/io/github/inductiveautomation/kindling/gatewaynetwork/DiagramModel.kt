package io.github.inductiveautomation.kindling.gatewaynetwork

import kotlinx.serialization.Serializable

/**
 * Contains the minimal data fields to be considered a valid gateway network diagram. There are more fields present
 * in an actual diagram, but they are all optional.
 */
@Serializable
data class DiagramModel(
    val localGatewayName: String,
    val redundantRole: String,
    val version: String,
    val edition: String,
    val connections: List<Connection> = emptyList(),
) {
    @Serializable
    data class Connection(
        val systemName: String,
        val connectionId: String,
        val connectionStatus: String,
        val redundantRole: String,
    )
}
