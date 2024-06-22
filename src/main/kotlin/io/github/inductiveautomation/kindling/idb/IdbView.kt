package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane.TabType
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.generic.GenericView
import io.github.inductiveautomation.kindling.idb.metrics.MetricsView
import io.github.inductiveautomation.kindling.idb.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.log.LogPanel
import io.github.inductiveautomation.kindling.log.MDC
import io.github.inductiveautomation.kindling.log.SystemLogEvent
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.toMap
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import kotlin.io.path.name

class IdbView(val path: Path) : ToolPanel() {
    private val connection = SQLiteConnection(path)

    private val tables: List<String> = connection.metaData
        .getTables("", "", "", null)
        .toList { rs -> rs[3] }

    private val tabs = TabStrip().apply {
        trailingComponent = null
        isTabsClosable = false
        tabType = TabType.underlined
        tabHeight = 16
        isHideTabAreaWithOneTab = true
    }

    init {
        name = path.name
        toolTipText = path.toString()

        tabs.addTab(
            tabName = "Tables",
            component = GenericView(connection),
            tabTooltip = null,
            select = true,
        )

        var addedTabs = 0
        for (tool in IdbTool.entries) {
            if (tool.supports(tables)) {
                tabs.addLazyTab(
                    tabName = tool.tabName,
                ) {
                    tool.open(connection)
                }
                addedTabs += 1
            }
        }
        if (addedTabs == 1) {
            tabs.selectedIndex = tabs.indices.last
        }

        add(tabs, "push, grow")
    }

    override val icon = IdbViewer.icon

    override fun removeNotify() {
        super.removeNotify()
        connection.close()
    }
}

enum class IdbTool {
    @Suppress("SqlResolve")
    Logs {
        override fun supports(tables: List<String>): Boolean = "logging_event" in tables

        override fun open(connection: Connection): ToolPanel {
            val stackTraces: Map<Long, List<String>> = connection.executeQuery(
                """
                    SELECT
                        event_id,
                        trace_line
                    FROM 
                        logging_event_exception
                    ORDER BY
                        event_id,
                        i
                """.trimIndent(),
            )
                .toMap<Long, MutableList<String>> { rs ->
                    val key: Long = rs["event_id"]
                    val valueList = getOrPut(key, ::mutableListOf)
                    valueList.add(rs["trace_line"])
                }

            val mdcKeys: Map<Long, List<MDC>> = connection.executeQuery(
                """
                    SELECT 
                        event_id,
                        mapped_key,
                        mapped_value
                    FROM 
                        logging_event_property
                    ORDER BY 
                        event_id
                """.trimIndent(),
            ).toMap<Long, MutableList<MDC>> { rs ->
                val key: Long = rs["event_id"]
                val valueList = getOrPut(key, ::mutableListOf)
                valueList +=
                    MDC(
                        rs["mapped_key"],
                        rs["mapped_value"],
                    )
            }

            val events = connection.executeQuery(
                """
                    SELECT
                           event_id,
                           timestmp,
                           formatted_message,
                           logger_name,
                           level_string,
                           thread_name
                    FROM 
                        logging_event
                    ORDER BY
                        timestmp
                """.trimIndent(),
            ).toList { rs ->
                val eventId: Long = rs["event_id"]
                SystemLogEvent(
                    timestamp = Instant.ofEpochMilli(rs["timestmp"]),
                    message = rs["formatted_message"],
                    logger = rs["logger_name"],
                    thread = rs["thread_name"],
                    level = enumValueOf(rs["level_string"]),
                    mdc = mdcKeys[eventId].orEmpty(),
                    stacktrace = stackTraces[eventId].orEmpty(),
                )
            }

            return LogPanel(events)
        }
    },
    Metrics {
        override fun supports(tables: List<String>): Boolean = "SYSTEM_METRICS" in tables
        override fun open(connection: Connection): ToolPanel = MetricsView(connection)
    },
    Images {
        override fun supports(tables: List<String>): Boolean = "IMAGES" in tables
        override fun open(connection: Connection): ToolPanel = ImagesPanel(connection)
    },
    TagConfig {
        override fun supports(tables: List<String>): Boolean = "TAGCONFIG" in tables
        override fun open(connection: Connection): ToolPanel = TagConfigView(connection)
        override val tabName: String = "Tag Config"
    },
    ;

    abstract fun supports(tables: List<String>): Boolean

    abstract fun open(connection: Connection): ToolPanel

    open val tabName: String = name
}

data object IdbViewer : Tool {
    override val serialKey = "idb-viewer"
    override val title = "SQLite Database"
    override val description = "SQLite Database (.idb)"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val filter = FileFilter(description, "idb", "db", "sqlite")

    override fun open(path: Path): ToolPanel = IdbView(path)
}
