package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics.MappedStatistic
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics.ProviderStatistic
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.ReifiedTableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.table.AbstractTableModel

class ProviderStatisticsPanel :
    JPanel(MigLayout("fillx, ins 3 0 0 0, gap 10, wrap 2, hidemode 3")),
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
                    } as List<ProviderStatistic<Int>>

                    val mappedStats =
                        newProvider.providerStatistics.values.filterIsInstance<MappedStatistic>()

                    Pair(individualStats, mappedStats)
                }

                generalStatsTable.model = ReifiedListTableModel(individualStats, IndividualStatColumns)
                mappedStatsTables.forEachIndexed { i, table ->
                    table.model = MappedStatModel(mappedStats[i])
                }

                loading = false
            }
        }

    private val generalStatsTable = ReifiedJXTable(ReifiedListTableModel(emptyList(), IndividualStatColumns))
    private val generalStatsScrollPane = FlatScrollPane(generalStatsTable)

    private val mappedStatsTables = ProviderStatistics().values
        .filterIsInstance<MappedStatistic>()
        .map { ReifiedJXTable(MappedStatModel()) }

    private val mappedStatsScrollPanes = mappedStatsTables.map(::FlatScrollPane)

    private val throbber = JLabel(FlatSVGIcon("icons/bx-loader-circle.svg").derive(50, 50))

    private val generalStatsLabel = JLabel("General Statistics").apply {
        putClientProperty("FlatLaf.styleClass", "h3")
    }

    private val mappedStatsLabel = JLabel("Grouped Statistics").apply {
        putClientProperty("FlatLaf.styleClass", "h3")
    }

    private var loading: Boolean = false
        set(value) {
            field = value
            generalStatsLabel.isVisible = !value
            mappedStatsLabel.isVisible = !value

            generalStatsScrollPane.isVisible = !value
            mappedStatsScrollPanes.forEach {
                it.isVisible = !value
            }

            throbber.isVisible = value
        }

    init {
        add(throbber, "push, grow, span")
        add(generalStatsLabel, "growx, span")
        add(generalStatsScrollPane, "growx, span, h 250!")

        add(mappedStatsLabel, "growx, span")
        for (pane in mappedStatsScrollPanes) {
            add(pane, "growx")
        }

        // Show nothing on startup
        components.forEach { it.isVisible = false }
    }

    override fun customizePopupMenu(menu: JPopupMenu) = menu.removeAll()
}

@Suppress("unused")
object IndividualStatColumns : ColumnList<ProviderStatistic<Int>>() {
    val stat by column("Stat") { it.name }

    val value by column("Value") { it.value }
}

class MappedStatModel(
    private val data: MappedStatistic? = null,
) : AbstractTableModel(),
    ReifiedTableModel<Map.Entry<String, Int>> {
    @Suppress("unused")
    override val columns = object : ColumnList<Map.Entry<String, Int>>() {
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

    operator fun <R> get(row: Int, column: Column<Map.Entry<String, Int>, R>): R? = data?.value?.entries
        ?.elementAtOrNull(row)
        ?.let {
            column.getValue(it)
        }
}
