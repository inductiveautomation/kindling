package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.statistics.categories.DatabaseStatistics
import io.github.inductiveautomation.kindling.statistics.categories.DeviceStatistics
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics
import io.github.inductiveautomation.kindling.statistics.categories.MetaStatistics
import io.github.inductiveautomation.kindling.statistics.categories.OpcServerStatistics
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.zip.views.SinglePathView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.awt.Font
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder
import kotlin.io.path.extension
import kotlin.time.measureTimedValue

class GwbkStatsView(
    override val provider: FileSystemProvider,
    override val path: Path,
) : SinglePathView("ins 6, fill, wrap 2, gap 20, hidemode 3") {
    override val icon: Icon? = null
    override val tabName: String = "Statistics"
    override val closable: Boolean = false

    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    private val gatewayBackup = GatewayBackup(path)

    init {
        add(statCard(MetaStatistics::calculate, MetaStatisticsRenderer()), "growx, wrap")

        add(statCard(ProjectStatistics::calculate, ProjectStatisticsRenderer()), "growx, sg")
        add(statCard(DatabaseStatistics::calculate, DatabaseStatisticsRenderer()), "growx, sg")
        add(statCard(DeviceStatistics::calculate, DeviceStatisticsRenderer()), "growx, sg")
        add(statCard(OpcServerStatistics::calculate, OpcConnectionsStatisticsRenderer()), "growx, sg")
        add(statCard(GatewayNetworkStatistics::calculate, GatewayNetworkStatisticsRenderer()), "growx, sg")
    }

    private fun <T : Statistic> statCard(
        calculator: StatisticCalculator<T>,
        renderer: StatisticRenderer<T>,
    ): JPanel {
        val headerLabel =
            JLabel(renderer.title, renderer.icon, SwingConstants.LEFT).apply {
                font = font.deriveFont(Font.BOLD, 14F)
            }
        val subtitleLabel = JLabel()
        val throbber = JLabel(FlatSVGIcon("icons/bx-loader-circle.svg"))

        return JPanel(MigLayout("ins 4")).apply {
            border = LineBorder(UIManager.getColor("Component.borderColor"), 3, true)

            add(headerLabel, "pushx, growx")
            add(subtitleLabel, "wrap, ax right")
            add(throbber, "push, grow, span")

            BACKGROUND.launch {
                val (statistic, duration) =
                    measureTimedValue {
                        calculator.calculate(gatewayBackup)
                    }
                println("Calculated ${renderer.title} in $duration")
                EDT_SCOPE.launch {
                    if (statistic == null) {
                        this@GwbkStatsView.remove(this@apply)
                    } else {
                        with(renderer) {
                            val render = statistic.render()
                            headerLabel.text = renderer.title
                            statistic.subtitle()?.let { subtitleLabel.text = it }

                            remove(throbber)
                            add(render, "push, grow, span")
                        }
                    }
                    revalidate()
                }
            }
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.IO)

        fun isGatewayBackup(path: Path): Boolean = path.extension.lowercase() == "gwbk"
    }
}

interface StatisticRenderer<T : Statistic> {
    val title: String
    val icon: Icon?

    fun T.subtitle(): String? {
        return null
    }

    fun T.render(): JComponent
}
