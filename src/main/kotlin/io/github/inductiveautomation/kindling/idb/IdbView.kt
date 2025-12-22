package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane.TabType
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.generic.GenericView
import io.github.inductiveautomation.kindling.idb.metrics.MetricsView
import io.github.inductiveautomation.kindling.log.LogFile
import io.github.inductiveautomation.kindling.log.SystemLogPanel
import io.github.inductiveautomation.kindling.log.SystemLogPanel.Companion.parseLogs
import io.github.inductiveautomation.kindling.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import java.nio.file.Path
import javax.swing.SwingConstants
import kotlin.io.path.name

class IdbView(paths: List<Path>) : ToolPanel() {
    private val connections = paths.map(::IdbConnection)

    private val tabs = TabStrip(false).apply {
        isTabsClosable = false
        tabType = TabType.card
        tabHeight = 16
        isHideTabAreaWithOneTab = true
        tabPlacement = SwingConstants.LEFT
        tabRotation = FlatTabbedPane.TabRotation.auto
    }

    init {
        name = paths.first().name
        toolTipText = paths.joinToString("\n")

        val addedTabs = IdbTool.entries.filter { tool ->
            tool.supports(connections)
        }.map { tool ->
            tabs.addLazyTab(tool.tabName) { tool.open(connections) }
        }

        if (addedTabs.isNotEmpty()) {
            tabs.selectedIndex = tabs.indices.last
        }

        add(tabs, "push, grow")
    }

    override val icon = IdbViewer.icon

    override fun removeNotify() {
        super.removeNotify()

        for (connection in connections) {
            connection.close()
        }
    }
}

class IdbConnection(
    val path: Path,
) : AutoCloseable {
    val connection = SQLiteConnection(path)

    val tables = connection.metaData
        .getTables("", "", "", null)
        .toList<String> { rs -> rs["TABLE_NAME"] }

    @Suppress("SqlResolve")
    val systemName: String? by lazy {
        connection.executeQuery("SELECT systemname FROM sysprops").toList { rs -> rs.get<String>(1) }.first()
    }

    override fun close() {
        connection.close()
    }
}

private enum class IdbTool {
    Generic {
        override fun supports(connections: List<IdbConnection>): Boolean = connections.size == 1

        override fun open(connections: List<IdbConnection>): ToolPanel = GenericView(connections.single().connection)

        override val tabName: String = "Tables"
    },
    Metrics {
        override fun supports(connections: List<IdbConnection>): Boolean = connections.singleOrNull { "SYSTEM_METRICS" in it.tables } != null
        override fun open(connections: List<IdbConnection>): ToolPanel = MetricsView(connections.single().connection)
    },
    Images {
        override fun supports(connections: List<IdbConnection>): Boolean = connections.singleOrNull { "IMAGES" in it.tables } != null
        override fun open(connections: List<IdbConnection>): ToolPanel = ImagesPanel(connections.single())
    },
    TagConfig {
        override fun supports(connections: List<IdbConnection>): Boolean = connections.singleOrNull { "TAGCONFIG" in it.tables } != null
        override fun open(connections: List<IdbConnection>): ToolPanel = TagConfigView.fromIdb(connections.single().connection)

        override val tabName: String = "Tag Config"
    },
    Logs {
        override fun supports(connections: List<IdbConnection>): Boolean = connections.all { conn -> "logging_event" in conn.tables }

        override fun open(connections: List<IdbConnection>): ToolPanel {
            val paths = connections.map { it.path }

            val logFiles = connections.map {
                LogFile(it.connection.parseLogs())
            }

            return SystemLogPanel(paths, logFiles)
        }
    },
    ;

    open val tabName: String = name

    abstract fun supports(connections: List<IdbConnection>): Boolean

    abstract fun open(connections: List<IdbConnection>): ToolPanel
}

data object IdbViewer : MultiTool {
    override val serialKey = "idb-viewer"
    override val title = "SQLite Database"
    override val description = "SQLite Database (.idb)"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    override val extensions: Array<String> = arrayOf("idb", "db", "sqlite")

    override fun open(path: Path): ToolPanel = IdbView(listOf(path))

    override fun open(paths: List<Path>): ToolPanel = IdbView(paths)
}
