package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.decorator.HighlightPredicate
import org.jdesktop.swingx.sort.SortController
import org.jdesktop.swingx.table.ColumnControlButton
import java.awt.Color
import javax.swing.JComponent
import javax.swing.SortOrder
import javax.swing.UIManager
import javax.swing.table.TableModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * An implementation of JXTable that is specifically assigned a model class on construction and is guaranteed to always
 * contain an instance of that model class. Generally, use the standalone reified [ReifiedJXTable] function as a
 * pseudo-constructor.
 */
class ReifiedJXTable<T : TableModel>(
    model: T,
    private val modelClass: Class<T>,
    columns: ColumnList<*>?,
) : JXTable(model) {
    private val setup = true

    private val packLater: () -> Unit =
        debounce(
            waitTime = 500.milliseconds,
            coroutineScope = EDT_SCOPE,
            destinationFunction = ::packAll,
        )

    init {
        if (columns != null) {
            columnFactory = columns.toColumnFactory()
            createDefaultColumnsFromModel()
        }
        isColumnControlVisible = true

        setDefaultRenderer<String>(
            getText = { it },
            getTooltip = { it },
        )

        setSortOrderCycle(SortOrder.ASCENDING, SortOrder.DESCENDING, SortOrder.UNSORTED)

        // TODO header name as tooltip without breaking sorting

        addHighlighter(
            object : ColorHighlighter(HighlightPredicate.ODD) {
                override fun getBackground(): Color? = UIManager.getColor("UIColorHighlighter.stripingBackground")
            },
        )

        packLater()
        actionMap.remove("find")
    }

    override fun createDefaultColumnControl(): JComponent {
        return ColumnControlButton(this, FlatSVGIcon("icons/bx-column.svg").derive(0.8F))
    }

    @Suppress("UNCHECKED_CAST")
    override fun getModel(): T = super.getModel() as T

    override fun setModel(model: TableModel) {
        if (setup) { // not sure why this is required, something with Kotlin's late initialization
            require(model::class.java == modelClass) { "Expected $modelClass but got ${model::class.java}" }
        }
        val sortedColumn = sortedColumnIndex
        val sortedColumnIsVisible = convertColumnIndexToView(sortedColumn) != -1

        val sortOrder = if (sortedColumn >= 0) (rowSorter as SortController<*>).getSortOrder(sortedColumn) else null

        val previousColumnSizes =
            IntArray(columnCount) { i ->
                getColumnExt(i).preferredWidth
            }

        super.setModel(model)

        if (sortOrder != null && sortedColumnIsVisible) {
            setSortOrder(sortedColumn, sortOrder)
        }
        if (setup) {
            for (index in model.columnIndices) {
                val previousSize = previousColumnSizes.getOrNull(index)
                if (previousSize != null && index < columnCount) {
                    getColumnExt(index).preferredWidth = previousSize
                }
            }
            packLater()
        }
    }
}

/**
 * Pseudo-constructor for a [ReifiedJXTable]. [columns] can be explicitly specified, or if [model] is an instance of
 * [ReifiedTableModel], they will be assumed from the model.
 */
inline fun <reified T : TableModel> ReifiedJXTable(
    model: T,
    columns: ColumnList<*>? = null,
): ReifiedJXTable<T> {
    return ReifiedJXTable(model, T::class.java, columns ?: (model as? ReifiedTableModel<*>)?.columns)
}
