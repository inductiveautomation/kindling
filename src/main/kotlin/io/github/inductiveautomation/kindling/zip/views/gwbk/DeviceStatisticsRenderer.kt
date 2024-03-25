package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.statistics.categories.DeviceStatistics
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.asActionIcon
import javax.swing.Icon
import javax.swing.SortOrder

class DeviceStatisticsRenderer : StatisticRenderer<DeviceStatistics> {
    override val title: String = "Devices"
    override val icon: Icon = FlatSVGIcon("icons/bx-chip.svg").asActionIcon()

    override fun DeviceStatistics.subtitle() = "$enabled enabled, $total total"

    override fun DeviceStatistics.render() = FlatScrollPane(
        ReifiedJXTable(ReifiedListTableModel(devices, DeviceColumns)).apply {
            setDefaultRenderer<DeviceStatistics.Device>(
                getText = { it?.name },
                getTooltip = { it?.description },
            )

            setSortOrder(Name, SortOrder.ASCENDING)
        },
    )

    @Suppress("unused")
    companion object DeviceColumns : ColumnList<DeviceStatistics.Device>() {
        val Name by column { it }
        val Type by column {
            when (it.type) {
                "Dnp3Driver" -> "DNP3"
                "LogixDriver" -> "Logix"
                "ProgrammableSimulatorDevice" -> "Simulator"
                "TCPDriver" -> "TCP"
                "UDPDriver" -> "UDP"
                "com.inductiveautomation.BacnetIpDeviceType" -> "BACnet"
                "com.inductiveautomation.FinsTcpDeviceType" -> "FinsTCP"
                "com.inductiveautomation.FinsUdpDeviceType" -> "FinsUDP"
                "com.inductiveautomation.Iec61850DeviceType" -> "IEC61850"
                "com.inductiveautomation.MitsubishiTcpDeviceType" -> "MitsubishiTCP"
                "com.inductiveautomation.omron.NjDriver" -> "OmronNJ"
                else -> it.type.substringAfterLast('.')
            }
        }
        val Enabled by column { it.enabled }
    }
}
