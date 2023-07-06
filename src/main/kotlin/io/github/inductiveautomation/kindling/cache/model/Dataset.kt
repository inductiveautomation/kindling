package io.github.inductiveautomation.kindling.cache.model

import com.inductiveautomation.ignition.common.Dataset
import com.inductiveautomation.ignition.common.model.values.QualityCode
import com.inductiveautomation.ignition.common.sqltags.model.types.DataQuality
import java.io.ObjectInputStream

@Suppress("PropertyName")
abstract class AbstractDataset @JvmOverloads constructor(
    @JvmField
    protected var columnNames: List<String> = emptyList(),
    @JvmField
    protected var columnTypes: List<Class<*>> = emptyList(),
    @JvmField
    protected var qualityCodes: Array<Array<QualityCode>>? = null,
) : Dataset {
    @JvmField
    protected var _columnNamesLowercase = columnNames.map { it.lowercase() }

    override fun getColumnNames(): List<String> = columnNames
    override fun getColumnTypes(): List<Class<*>> = columnTypes
    override fun getColumnCount(): Int = columnNames.size
    abstract override fun getRowCount(): Int
    override fun getColumnIndex(columnName: String): Int = columnNames.indexOf(columnName)
    override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]
    override fun getColumnType(columnIndex: Int): Class<*> = columnTypes[columnIndex]

    @Suppress("UNCHECKED_CAST")
    private fun readObject(ois: ObjectInputStream) {
        this._columnNamesLowercase = ois.readObject() as List<String>
        columnNames = ois.readObject() as List<String>
        columnTypes = ois.readObject() as List<Class<*>>
        val qualities = ois.readObject()
        qualityCodes = when {
            qualities is Array<*> && qualities.isArrayOf<Array<QualityCode>>() -> qualities as Array<Array<QualityCode>>
            qualities is Array<*> && qualities.isArrayOf<Array<DataQuality>>() -> (qualities as Array<Array<DataQuality>>).convertToQualityCodes()
            else -> null
        }
    }

    companion object {
        @JvmStatic
        private val serialVersionUID = -6392821391144181995L

        private fun Array<Array<DataQuality>>.convertToQualityCodes(): Array<Array<QualityCode>> {
            val columns = size
            val rows = firstOrNull()?.size ?: 0
            return Array(columns) { column ->
                Array(rows) { row ->
                    this[column][row].qualityCode
                }
            }
        }
    }
}

class BasicDataset @JvmOverloads constructor(
    private val data: Array<Array<Any>> = emptyArray(),
    columnNames: List<String> = emptyList(),
    columnTypes: List<Class<*>> = emptyList(),
    qualityCodes: Array<Array<QualityCode>>? = null,
) : AbstractDataset(columnNames, columnTypes, qualityCodes) {
    override fun getColumnNames(): List<String> = columnNames
    override fun getColumnTypes(): List<Class<*>> = columnTypes
    override fun getColumnCount(): Int = columnNames.size
    override fun getRowCount(): Int = data.firstOrNull()?.size ?: 0
    override fun getColumnIndex(columnName: String): Int = columnNames.indexOf(columnName)
    override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]
    override fun getColumnType(columnIndex: Int): Class<*> = columnTypes[columnIndex]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = data[rowIndex][columnIndex]
    override fun getValueAt(rowIndex: Int, columnName: String): Any = getValueAt(rowIndex, getColumnIndex(columnName))
    override fun getQualityAt(rowIndex: Int, columnIndex: Int): QualityCode = QualityCode.Good
    override fun getPrimitiveValueAt(rowIndex: Int, columnIndex: Int): Double = Double.NaN

    companion object {
        @JvmStatic
        private val serialVersionUID = 3264911947104906591L
    }
}
