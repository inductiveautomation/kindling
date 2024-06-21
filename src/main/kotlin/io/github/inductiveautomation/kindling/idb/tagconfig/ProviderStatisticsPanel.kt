@file:Suppress("unused")

package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.table.AbstractTableModel

class ProviderStatisticsPanel :
    JPanel(MigLayout("fillx, ins 0, gap 10px, wrap 2, hidemode 3")),
    PopupMenuCustomizer {
    var provider: TagProviderRecord? = null
        set(newProvider) {
            field = newProvider

            if (newProvider == null) return

            EDT_SCOPE.launch {
                // Wait for the provider to load, but wait on another thread.
                loading = true

                val (individualStats, mappedStats) = withContext(Dispatchers.Default) {
                    newProvider.loadProvider.join() // Wait for provider to load

                    @Suppress("UNCHECKED_CAST")
                    val individualStats = newProvider.providerStatistics.values.filter {
                        it is ProviderStatistics.QuantitativeStatistic || it is ProviderStatistics.DependentStatistic<*, *>
                    } as List<ProviderStatistics.ProviderStatistic<Int>>

                    val mappedStats =
                        newProvider.providerStatistics.values.filterIsInstance<ProviderStatistics.MappedStatistic>()

                    Pair(individualStats, mappedStats)
                }

                generalStatsTable.model = IndividualStatsModel(individualStats)
                mappedStatsTables.forEachIndexed { i, table ->
                    table.model = MappedStatModel(mappedStats[i])
                }

                loading = false
            }
        }

    private val generalStatsTable = ReifiedJXTable(IndividualStatsModel(), IndividualStatsModel.IndividualStatColumns)
    private val generalStatsScrollPane = FlatScrollPane(generalStatsTable)

    private val mappedStatsTables =
        List(ProviderStatistics().values.filterIsInstance<ProviderStatistics.MappedStatistic>().size) {
            val model = MappedStatModel()
            ReifiedJXTable(model, model.columns)
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

class IndividualStatsModel(
    private val data: List<ProviderStatistics.ProviderStatistic<Int>> = emptyList(),
) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = IndividualStatColumns[column].header
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, IndividualStatColumns[column])
    override fun getColumnClass(column: Int): Class<*> = IndividualStatColumns[column].clazz
    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

    operator fun <R> get(row: Int, column: Column<ProviderStatistics.ProviderStatistic<Int>, R>): R? = data.getOrNull(row)?.let { stat ->
        column.getValue(stat)
    }

    companion object IndividualStatColumns : ColumnList<ProviderStatistics.ProviderStatistic<Int>>() {
        val stat by column("Stat") {
            it.name
        }

        val value by column("Value") {
            it.value
        }
    }
}

class MappedStatModel(
    private val data: ProviderStatistics.MappedStatistic? = null,
) : AbstractTableModel() {
    val columns = object : ColumnList<Map.Entry<String, Int>>() {
        val statName by column(data?.humanReadableName) {
            it.key
        }

        val statValue by column("Value") {
            it.value
        }
    }

    override fun getColumnName(column: Int): String = columns[column].header
    override fun getRowCount(): Int = data?.value?.size ?: 0
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])
    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz
    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

    operator fun <R> get(row: Int, column: Column<Map.Entry<String, Int>, R>): R? = data?.value?.entries?.toList()?.getOrNull(row)?.let {
        column.getValue(it)
    }
}
