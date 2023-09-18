package io.github.inductiveautomation.kindling.utils

import org.jdesktop.swingx.table.ColumnFactory
import org.jdesktop.swingx.table.TableColumnExt
import javax.swing.table.TableModel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ColumnList<R> private constructor(
    @PublishedApi internal val list: MutableList<Column<R, *>>,
) : List<Column<R, *>> by list {
    constructor() : this(mutableListOf())

    /**
     * Defines a new column (type T). Uses the name of the property if [name] isn't provided.
     */
    // This is some real Kotlin 'magic', but makes it very easy to define JTable models that can be used type-safely
    protected inline fun <reified T> column(
        name: String? = null,
        noinline column: (TableColumnExt.(model: TableModel) -> Unit)? = null,
        noinline value: (R) -> T,
    ): PropertyDelegateProvider<ColumnList<R>, ReadOnlyProperty<ColumnList<R>, Column<R, T>>> {
        return PropertyDelegateProvider { thisRef, prop ->
            val actual = Column(
                header = name ?: prop.name,
                getValue = value,
                columnCustomization = column,
                clazz = T::class.java,
            )
            thisRef.add(actual)
            ReadOnlyProperty { _, _ -> actual }
        }
    }

    fun add(column: Column<R, *>) {
        list.add(column)
    }

    operator fun get(column: Column<*, *>): Int = indexOf(column)

    fun toColumnFactory() = object : ColumnFactory() {
        override fun configureTableColumn(model: TableModel, columnExt: TableColumnExt) {
            super.configureTableColumn(model, columnExt)
            val column = list[columnExt.modelIndex]
            columnExt.toolTipText = column.header
            columnExt.identifier = column
            column.columnCustomization?.invoke(columnExt, model)
        }
    }
}
