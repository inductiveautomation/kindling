package io.github.inductiveautomation.kindling.utils

import org.jdesktop.swingx.table.TableColumnExt
import javax.swing.table.TableModel

data class Column<R, C>(
    val header: String,
    val getValue: (row: R) -> C,
    val columnCustomization: (TableColumnExt.(model: TableModel) -> Unit)?,
    val clazz: Class<C>,
) {
    companion object {
        inline operator fun <R, reified C> invoke(
            header: String,
            noinline columnCustomization: (TableColumnExt.(model: TableModel) -> Unit)? = null,
            noinline getValue: (row: R) -> C,
        ) = Column(
            header = header,
            columnCustomization = columnCustomization,
            getValue = getValue,
            clazz = C::class.java,
        )
    }
}
