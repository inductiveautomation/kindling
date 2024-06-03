package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.traverseChildren
import java.awt.Container
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTable

class ProviderStatisticsPanel : JPanel(MigLayout("fillx, ins 0, gap 10px, wrap 2, hidemode 3")), PopupMenuCustomizer {
    var provider: TagProviderRecord? = null
        set(newProvider) {
            field = newProvider

            if (newProvider == null) return

            EDT_SCOPE.launch {
                // Wait for the provider to load, but wait on another thread.
                loading = true

                val (individualStats, mappedStats) = withContext(Dispatchers.Default) {
                    newProvider.loadProvider.join()

                    val individualStats = newProvider.providerStatistics.values.filter {
                        it is ProviderStatistics.QuantitativeStatistic || it is ProviderStatistics.DependentStatistic<*, *>
                    }.map {
                        arrayOf(it.humanReadableName, it.value.toString())
                    }.toTypedArray()

                    val mappedStats = newProvider.providerStatistics.values.filterIsInstance<ProviderStatistics.MappedStatistic>()

                    Pair(individualStats, mappedStats)
                }

                generalStatsTable.model = DefaultTableModel(individualStats, arrayOf("Stat", "Value"))
                mappedStatsTables.forEachIndexed { i, table ->
                    val mappedStat = mappedStats[i]
                    val entries = mappedStat.value.entries.map {
                        arrayOf(it.key, it.value.toString())
                    }.toTypedArray()

                    table.model = DefaultTableModel(entries, arrayOf(mappedStat.humanReadableName, "Value"))
                }

                loading = false
            }
        }

    private val generalStatsTable = JXTable()
    private val generalStatsScrollPane = FlatScrollPane(generalStatsTable)

    private val mappedStatsTables = List(ProviderStatistics().values.filterIsInstance<ProviderStatistics.MappedStatistic>().size) {
        JXTable()
    }
    private val mappedStatsScrollPanes = mappedStatsTables.map {
        FlatScrollPane(it)
    }

    private val throbber = JLabel(FlatSVGIcon("icons/bx-loader-circle.svg").derive(50, 50))

    private val generalStatsLabel = JLabel("General Statistics").apply {
        font = font.deriveFont(Font.BOLD, 16F)
        horizontalAlignment = JLabel.CENTER
    }

    private val mappedStatsLabel = JLabel("Grouped Statistics").apply {
        font = font.deriveFont(Font.BOLD, 16F)
        horizontalAlignment = JLabel.CENTER
    }

    private var loading: Boolean = false
        set(value) {
            field = value
            generalStatsLabel.isVisible = !value
            mappedStatsLabel.isVisible = !value

            generalStatsScrollPane.isVisible = !value
            mappedStatsScrollPanes.forEach { it.isVisible = !value }

            throbber.isVisible = value
        }

    init {
        add(throbber, "push, grow, span")
        add(generalStatsLabel, "growx, span")
        add(generalStatsScrollPane, "growx, span, h 250!")

        add(mappedStatsLabel, "growx, span")
        mappedStatsScrollPanes.forEach { pane ->
            add(pane, "growx")
        }

        // Show nothing on startup
        components.forEach { it.isVisible = false }
    }

    override fun customizePopupMenu(menu: JPopupMenu) = menu.removeAll()
}
