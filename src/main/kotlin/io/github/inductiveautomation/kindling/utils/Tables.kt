package io.github.inductiveautomation.kindling.utils

import io.github.evanrupert.excelkt.workbook
import java.io.File
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel

fun JTable.selectedRowIndices(): IntArray {
    return selectionModel.selectedIndices
        .filter { isRowSelected(it) }
        .map { convertRowIndexToModel(it) }
        .toIntArray()
}

fun JTable.selectedOrAllRowIndices(): IntArray {
    return if (selectionModel.isSelectionEmpty) {
        IntArray(model.rowCount) { it }
    } else {
        selectedRowIndices()
    }
}

val TableModel.rowIndices get() = 0 until rowCount
val TableModel.columnIndices get() = 0 until columnCount

/**
 * A custom [TableModelEvent] which is fired when an unspecified number of row data has changed for a single column.
 *
 * @param column The column index.
 */
fun AbstractTableModel.fireTableColumnDataChanged(column: Int) {
    fireTableChanged(
        object : TableModelEvent(this) {
            override fun getColumn(): Int = column
        },
    )
}

fun TableModel.exportToCSV(file: File) {
    file.printWriter().use { out ->
        columnIndices.joinTo(buffer = out, separator = ",") { col ->
            getColumnName(col)
        }
        out.println()
        for (row in rowIndices) {
            columnIndices.joinTo(buffer = out, separator = ",") { col ->
                "\"${getValueAt(row, col)?.toString().orEmpty()}\""
            }
            out.println()
        }
    }
}

fun TableModel.exportToXLSX(file: File) = file.outputStream().use { fos ->
    workbook {
        sheet("Sheet 1") { // TODO: Some way to pipe in a more useful sheet name (or multiple sheets?)
            row {
                for (col in columnIndices) {
                    cell(getColumnName(col))
                }
            }
            for (row in rowIndices) {
                row {
                    for (col in columnIndices) {
                        when (val value = getValueAt(row, col)) {
                            is Double -> cell(
                                value,
                                createCellStyle {
                                    dataFormat = xssfWorkbook.createDataFormat().getFormat("0.00")
                                },
                            )

                            else -> cell(value ?: "")
                        }
                    }
                }
            }
        }
    }.xssfWorkbook.write(fos)
}
