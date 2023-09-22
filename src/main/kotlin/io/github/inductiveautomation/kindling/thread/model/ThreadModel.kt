package io.github.inductiveautomation.kindling.thread.model

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.mode
import org.jdesktop.swingx.renderer.CellContext
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.LabelProvider
import org.jdesktop.swingx.renderer.StringValues
import java.awt.Font
import java.lang.Thread.State.BLOCKED
import java.lang.Thread.State.NEW
import java.lang.Thread.State.RUNNABLE
import java.lang.Thread.State.TIMED_WAITING
import java.lang.Thread.State.WAITING
import java.text.DecimalFormat
import javax.swing.table.AbstractTableModel
import java.lang.Thread.State as ThreadState

// A thread's lifespan across multiple thread dumps
typealias ThreadLifespan = List<Thread?>

sealed class ThreadColumnList : ColumnList<ThreadLifespan>() {
    private val percent = DecimalFormat("0.000'%'")

    val mark = Column<ThreadLifespan, Boolean>(
        header = "Mark",
        columnCustomization = {
            minWidth = 25
            maxWidth = 25
            toolTipText = "Marked Threads"
            headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
            }
        },
        getValue = { it.firstNotNullOf { thread -> thread?.marked } },
    )

    val id = Column<ThreadLifespan, Int>(
        header = "Id",
        columnCustomization = {
            minWidth = 50
            maxWidth = 75
            cellRenderer = DefaultTableRenderer(Any?::toString)
        },
        getValue = { it.firstNotNullOf { thread -> thread?.id } },
    )

    val name = Column<ThreadLifespan, String>(
        header = "Name",
        getValue = { threads ->
            threads.firstNotNullOf { thread -> thread?.name }
        },
    )

    val cpu = Column<ThreadLifespan, Double?>(
        header = "CPU",
        columnCustomization = {
            minWidth = 60
            cellRenderer = DefaultTableRenderer { value ->
                (value as? Double)?.let { percent.format(it) }.orEmpty()
            }
        },
        getValue = { threads ->
            threads.maxOfOrNull { thread -> thread?.cpuUsage ?: 0.0 }
        },
    )

    val depth = Column<ThreadLifespan, Int>(
        header = "Depth",
        columnCustomization = {
            minWidth = 50
        },
        getValue = { threads ->
            threads.maxOf { thread -> thread?.stacktrace?.size ?: 0 }
        },
    )

    val system = Column<ThreadLifespan, String?>(
        header = "System",
        columnCustomization = {
            isVisible = false
            minWidth = 75
            cellRenderer = DefaultTableRenderer { value ->
                (value as? String) ?: "Unassigned"
            }
        },
        getValue = { threads ->
            threads.firstNotNullOfOrNull { thread -> thread?.system }
        },
    )

    val pool = Column<ThreadLifespan, String?>(
        header = "Pool",
        columnCustomization = {
            isVisible = false
            minWidth = 75
            cellRenderer = DefaultTableRenderer { value ->
                (value as? String) ?: "(No Pool)"
            }
        },
        getValue = { threads ->
            threads.firstNotNullOfOrNull { thread -> thread?.pool }
        },
    )

    abstract val filterableColumns: List<Column<ThreadLifespan, out Any?>>
    abstract val markableColumns: List<Column<ThreadLifespan, out Any?>>
}

class ThreadModel(val threadData: List<ThreadLifespan>) : AbstractTableModel() {
    init {
        require(threadData.all { it.isNotEmpty() }) { "Cannot aggregate empty list of thread dumps" }
    }

    val isSingleContext: Boolean = threadData.groupingBy { lifespan -> lifespan.count { thread -> thread != null } }.mode() == 1

    val columns = if (isSingleContext) SingleThreadColumns else MultiThreadColumns

    override fun getColumnName(column: Int): String = columns[column].header
    override fun getRowCount(): Int = threadData.size
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])
    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz
    operator fun get(row: Int): ThreadLifespan {
        return threadData[row]
    }

    operator fun <T> get(row: Int, column: Column<ThreadLifespan, T>): T {
        return get(row).let { info ->
            column.getValue(info)
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == columns[columns.mark]
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        require(isCellEditable(rowIndex, columnIndex))
        threadData[rowIndex].forEach {
            it?.marked = aValue as Boolean
        }
    }

    val markIndex = columns[
        when (columns) {
            is SingleThreadColumns -> SingleThreadColumns.mark
            is MultiThreadColumns -> MultiThreadColumns.mark
        },
    ]

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    data object MultiThreadColumns : ThreadColumnList() {
        private val MONOSPACED = Font(Font.MONOSPACED, Font.PLAIN, 12)

        val state = Column<ThreadLifespan, String>(
            "State",
            columnCustomization = {
                minWidth = 105
                cellRenderer = DefaultTableRenderer(
                    object : LabelProvider() {
                        override fun configureVisuals(context: CellContext?) {
                            super.configureVisuals(context).also {
                                rendererComponent.font = MONOSPACED
                            }
                        }
                    },
                )
            },
            getValue = { threadList ->
                threadList.joinToString(" → ") { thread ->
                    when (thread?.state) {
                        NEW -> "N"
                        RUNNABLE -> "R"
                        BLOCKED -> "B"
                        WAITING -> "W"
                        TIMED_WAITING -> "T"
                        else -> "X"
                    }
                }
            },
        )

        val blocker = Column<ThreadLifespan, Boolean>(
            "Blocker",
            columnCustomization = {
                minWidth = 60
                maxWidth = 60
            },
            getValue = { threads ->
                threads.any { thread -> thread?.blocker?.owner != null }
            },
        )

        init {
            add(mark)
            add(id)
            add(name)
            add(state)
            add(cpu.copy(header = "Max CPU"))
            add(depth.copy(header = "Max Depth"))
            add(blocker)
            add(system)
            add(pool)
        }

        override val filterableColumns = listOf(
            system,
            pool,
        )

        override val markableColumns = listOf(
            system,
            pool,
            blocker,
        )
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    data object SingleThreadColumns : ThreadColumnList() {
        val state = Column<ThreadLifespan, ThreadState>(
            "State",
            columnCustomization = {
                minWidth = 105
                maxWidth = 105
            },
            getValue = { threads ->
                threads.firstNotNullOf { thread -> thread?.state }
            },
        )

        val daemon = Column<ThreadLifespan, Boolean>(
            header = "Daemon",
            columnCustomization = {
                minWidth = 55
                maxWidth = 55
            },
            getValue = { threads ->
                threads.any { thread -> thread?.isDaemon == true }
            },
        )

        val blocker = Column<ThreadLifespan, Int?>(
            "Blocker",
            columnCustomization = {
                minWidth = 60
                maxWidth = 60
            },
            getValue = { threads ->
                threads.firstNotNullOfOrNull { thread -> thread?.blocker?.owner }
            },
        )

        val stacktrace = Column<ThreadLifespan, String>(
            header = "Stacktrace",
            columnCustomization = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "No Trace"
                }
            },
            getValue = { threads ->
                threads.firstNotNullOf { thread -> thread?.stacktrace }.joinToString()
            },
        )

        val scope = Column<ThreadLifespan, String?>(
            header = "Scope",
            columnCustomization = {
                isVisible = false
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "Unknown"
                }
            },
            getValue = { threads ->
                threads.firstNotNullOfOrNull { thread -> thread?.scope }
            },
        )

        init {
            add(mark)
            add(id)
            add(state)
            add(name)
            add(daemon)
            add(depth)
            add(cpu)
            add(system)
            add(pool)
            add(blocker)
            add(stacktrace)
            add(scope)
        }

        override val filterableColumns = listOf(
            state,
            system,
            pool,
        )

        override val markableColumns = listOf(
            state,
            system,
            pool,
            blocker,
            stacktrace,
        )
    }
}
