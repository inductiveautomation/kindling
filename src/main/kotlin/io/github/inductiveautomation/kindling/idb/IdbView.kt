package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane.TabType
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Preference
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.core.PreferenceCategory
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.generic.GenericView
import io.github.inductiveautomation.kindling.idb.metrics.MetricsView
import io.github.inductiveautomation.kindling.idb.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.log.LogFile
import io.github.inductiveautomation.kindling.log.SystemLogPanel
import io.github.inductiveautomation.kindling.log.SystemLogPanel.Companion.parseLogs
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import org.sqlite.SQLiteConnection
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.name

class IdbView(paths: List<Path>) : ToolPanel() {
    private val data = paths.map(::IdbFileData)

    private val tabs = TabStrip().apply {
        trailingComponent = null
        isTabsClosable = false
        tabType = TabType.underlined
        tabHeight = 16
        isHideTabAreaWithOneTab = true
    }

    init {
        name = paths.first().name
        toolTipText = paths.joinToString("\n")

        var addedTabs = 0

        /*
         * Not doing partial subsets of files for now.
         * (e.g. Multitool X supports files A and B, Multitool Y supports files C and D)
         * i.e. we assume the user is opening multiple IDBs they expect to work together
         */
        val supportedMultiTools = MultiIdbTool.entries.filter { tool ->
            data.all { tool.supports(it.tables) }
        }

        if (supportedMultiTools.isNotEmpty()) {
            if (IdbViewer.ShowGenericViewWithMultiTools.currentValue || paths.size == 1) {
                for ((path, connection, _) in data) {
                    tabs.addTab(
                        tabName = path.name,
                        component = GenericView(connection),
                        tabTooltip = null,
                        select = true,
                    )
                }
            }
            for (tool in supportedMultiTools) {
                tabs.addLazyTab(tabName = tool.tabName) { tool.open(data) }
            }
            addedTabs = supportedMultiTools.size
        } else {
            for ((_, connection) in data) {
                tabs.addTab(
                    tabName = "Tables",
                    component = GenericView(connection),
                    tabTooltip = null,
                    select = true,
                )
            }

            for (tool in SingleIdbTool.entries) {
                for (idbFile in data) {
                    if (tool.supports(idbFile.tables)) {
                        tabs.addLazyTab(
                            tabName = tool.tabName,
                        ) {
                            tool.open(idbFile)
                        }
                        addedTabs += 1
                    }
                }
            }
        }

        if (addedTabs > 0) {
            tabs.selectedIndex = tabs.indices.last
        }

        add(tabs, "push, grow")
    }

    override val icon = IdbViewer.icon

    override fun removeNotify() {
        super.removeNotify()
        data.forEach { it.connection.close() }
    }

    companion object {
        fun Connection.getAllTableNames(): List<String> {
            if (this !is SQLiteConnection) return emptyList()
            return metaData
                .getTables("", "", "", null)
                .toList { rs -> rs[3] }
        }
    }

    internal class IdbFileData(val path: Path) {
        val connection = SQLiteConnection(path)
        val tables = connection.getAllTableNames()

        operator fun component1() = path
        operator fun component2() = connection
        operator fun component3() = tables
    }
}

private sealed interface IdbTool {
    fun supports(tables: List<String>): Boolean

    fun open(fileData: IdbView.IdbFileData): ToolPanel

    val tabName: String
}

private enum class SingleIdbTool : IdbTool {
    Metrics {
        override fun supports(tables: List<String>): Boolean = "SYSTEM_METRICS" in tables
        override fun open(fileData: IdbView.IdbFileData): ToolPanel = MetricsView(fileData.connection)
    },
    Images {
        override fun supports(tables: List<String>): Boolean = "IMAGES" in tables
        override fun open(fileData: IdbView.IdbFileData): ToolPanel = ImagesPanel(fileData.connection)
    },
    TagConfig {
        override fun supports(tables: List<String>): Boolean = "TAGCONFIG" in tables
        override fun open(fileData: IdbView.IdbFileData): ToolPanel = TagConfigView(fileData.connection)
        override val tabName: String = "Tag Config"
    },
    ;

    override val tabName: String = name
}

private enum class MultiIdbTool : IdbTool {
    Logs {
        override fun supports(tables: List<String>): Boolean = "logging_event" in tables
        override fun open(fileData: List<IdbView.IdbFileData>): ToolPanel {
            val paths = fileData.map { it.path }

            val logFiles = fileData.map { (_, connection, _) ->
                LogFile(
                    connection.parseLogs().also { connection.close() },
                )
            }

            return SystemLogPanel(paths, logFiles)
        }
    },
    ;

    abstract fun open(fileData: List<IdbView.IdbFileData>): ToolPanel
    override fun open(fileData: IdbView.IdbFileData): ToolPanel = open(listOf(fileData))
    override val tabName = name
}

data object IdbViewer : MultiTool, PreferenceCategory {
    override val serialKey = "idb-viewer"
    override val title = "SQLite Database"
    override val description = "SQLite Database (.idb)"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val filter = FileFilter(description, "idb", "db", "sqlite")

    override fun open(path: Path): ToolPanel = IdbView(listOf(path))

    override fun open(paths: List<Path>): ToolPanel = IdbView(paths)

    override val displayName = "IDB Viewer"

    val ShowGenericViewWithMultiTools: Preference<Boolean> = preference(
        name = "Include Generic IDB Tabs with Multiple Files",
        default = false,
        editor = {
            PreferenceCheckbox("Include tabs for Generic IDB browser when opening multiple IDB Files")
        },
    )

    override val preferences: List<Preference<*>> = listOf(ShowGenericViewWithMultiTools)
}
