package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import net.miginfocom.swing.MigLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable

class ProviderStatisticsPanel : JScrollPane(), PopupMenuCustomizer {
    var provider: TagProviderRecord? = null
        set(newProvider) {
            field = newProvider
            mainPanel.removeAll()

            newProvider?.providerStatistics?.all?.forEach {
                mainPanel.add(StatisticCard(it), "grow, sizegroup")
            }
        }

    private val mainPanel = JPanel(MigLayout("fill, ins 0, gap 10px, wrap 3"))

    init {
        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
        setViewportView(mainPanel)
    }

    override fun customizePopupMenu(menu: JPopupMenu) = menu.removeAll()

    class StatisticCard<T>(
        private val statistic: ProviderStatistics.ProviderStatistic<T>,
    ) : JPanel(MigLayout("fill, ins 0")) {
        private val header = JLabel(statistic.humanReadableName, JLabel.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 20F)
        }

        private val data: JComponent = when (statistic) {
            is ProviderStatistics.QuantitativeStatistic,
            is ProviderStatistics.DependentStatistic<*, *>,
            -> JLabel(statistic.value.toString(), JLabel.CENTER).apply {
                verticalAlignment = JLabel.NORTH
                font = font.deriveFont(25F)
            }

            is ProviderStatistics.ListStatistic<*> -> JList(statistic.value.toTypedArray()).apply {
                selectionModel = NoSelectionModel()
            }

            is ProviderStatistics.MappedStatistic -> {
                JPanel(MigLayout("fill, ins 0, wrap 2")).apply {
                    val cols = arrayOf("Key", "Value")
                    val rows = statistic.value.entries.map { (key, value) ->
                        arrayOf(key, value)
                    }.toTypedArray()

                    add(JTable(rows, cols), "push, grow")
                }
            }
        }

        init {
            border = BorderFactory.createEtchedBorder()
            add(header, "growx, wrap")
            add(data, "push, grow, span")
        }
    }
}
