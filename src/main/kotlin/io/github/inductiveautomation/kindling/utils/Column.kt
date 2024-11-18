package io.github.inductiveautomation.kindling.utils

import org.jdesktop.swingx.table.TableColumnExt
import javax.swing.table.TableModel

data class Column<R, C>(
    val header: String,
    val getValue: GetValue<R, C>,
    val setValue: SetValue<R, C>?,
    val columnCustomization: (TableColumnExt.(model: TableModel) -> Unit)?,
    val clazz: Class<C>,
) {
    companion object {
        inline operator fun <R, reified C> invoke(
            header: String,
            noinline columnCustomization: (TableColumnExt.(model: TableModel) -> Unit)? = null,
            noinline setValue: SetValue<R, C>? = null,
            noinline getValue: GetValue<R, C>,
        ) = Column(
            header = header,
            columnCustomization = columnCustomization,
            setValue = setValue,
            getValue = getValue,
            clazz = C::class.java,
        )
    }
}

typealias GetValue<R, C> = (row: R) -> C

typealias SetValue<R, C> = R.(value: C?) -> Unit
