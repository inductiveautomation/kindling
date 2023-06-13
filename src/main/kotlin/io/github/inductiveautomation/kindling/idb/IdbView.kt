package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.generic.GenericView
import io.github.inductiveautomation.kindling.idb.metrics.MetricsView
import io.github.inductiveautomation.kindling.log.Level
import io.github.inductiveautomation.kindling.log.LogPanel
import io.github.inductiveautomation.kindling.log.SystemLogsEvent
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.toList
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import kotlin.io.path.name

class IdbView(val path: Path) : ToolPanel() {
    private val connection = SQLiteConnection(path)

    private val tables: List<String> = connection.metaData.getTables("", "", "", null).toList { rs ->
        rs.getString(3)
    }

    private val tabs = TabStrip().apply {
        trailingComponent = null
        isTabsClosable = false
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
        for (tool in IdbTool.values()) {
            if (tool.supports(tables)) {
                tabs.addLazyTab(
                    tabName = tool.name,
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
            val stackTraces: Map<Int, List<String>> = connection.prepareStatement(
                //language=sql
                """
                SELECT
                    event_id,
                    i,
                    trace_line
                FROM 
                    logging_event_exception
                ORDER BY
                    event_id,
                    i
                """.trimIndent(),
            ).executeQuery()
                .toList { resultSet ->
                    Pair(
                        resultSet.getInt("event_id"),
                        resultSet.getString("trace_line"),
                    )
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

            val mdcKeys: Map<Int, Map<String, String>> = connection.prepareStatement(
                //language=sql
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
            ).executeQuery()
                .toList { resultSet ->
                    Triple(
                        resultSet.getInt("event_id"),
                        resultSet.getString("mapped_key"),
                        resultSet.getString("mapped_value"),
                    )
                }.groupingBy { it.first }
                .aggregateTo(mutableMapOf<Int, MutableMap<String, String>>()) { _, accumulator, element, _ ->
                    val acc = accumulator ?: mutableMapOf()
                    acc[element.second] = element.third ?: "null"
                    acc
                }

            val events = connection.prepareStatement(
                //language=sql
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
                    event_id
                """.trimIndent(),
            ).executeQuery()
                .toList { resultSet ->
                    val eventId = resultSet.getInt("event_id")
                    SystemLogsEvent(
                        timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
                        message = resultSet.getString("formatted_message"),
                        logger = resultSet.getString("logger_name"),
                        thread = resultSet.getString("thread_name"),
                        level = Level.valueOf(resultSet.getString("level_string")),
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
//    Images {
//        override fun supports(tables: List<String>): Boolean = "IMAGES" in tables
//        override fun open(connection: Connection): ToolPanel = ImagesPanel(connection)
//    }
    ;

    abstract fun supports(tables: List<String>): Boolean

    abstract fun open(connection: Connection): ToolPanel
}

object IdbViewer : Tool {
    override val title = "Idb File"
    override val description = ".idb (SQLite3) files"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val extensions = listOf("idb")
    override fun open(path: Path): ToolPanel = IdbView(path)
}
