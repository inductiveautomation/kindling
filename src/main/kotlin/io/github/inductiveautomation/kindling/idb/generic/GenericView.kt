package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.components.FlatSplitPane
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.db.Column
import io.github.inductiveautomation.kindling.core.db.QueryResult
import io.github.inductiveautomation.kindling.core.db.ResultsPanel
import io.github.inductiveautomation.kindling.core.db.SortableTree
import io.github.inductiveautomation.kindling.core.db.Table
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.RSyntaxTextArea
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.containsInOrder
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.javaType
import io.github.inductiveautomation.kindling.utils.menuShortcutKeyMaskEx
import io.github.inductiveautomation.kindling.utils.toList
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.event.KeyEvent
import java.sql.Connection
import java.sql.Date
import java.sql.JDBCType
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke

class GenericView(connection: Connection) : ToolPanel("ins 0, fill, hidemode 3") {
    @Suppress("SqlResolve")
    private val tables: List<Table> = connection
        .executeQuery("""SELECT name FROM main.sqlite_schema WHERE type = "table" ORDER BY name""")
        .toList { resultSet ->
            resultSet.getString(1)
        }.mapIndexed { i, tableName ->
            Table(
                name = tableName,
                _parent = { sortableTree.root },
                columns = connection
                    .executeQuery("""PRAGMA table_xinfo("$tableName");""")
                    .toList { resultSet ->
                        Column(
                            name = resultSet["name"],
                            type = resultSet["type"],
                            notNull = resultSet["notnull"],
                            defaultValue = resultSet["dflt_value"],
                            primaryKey = resultSet["pk"],
                            hidden = resultSet["hidden"],
                            _parent = { sortableTree.root.getChildAt(i) },
                        )
                    },
                size = connection
                    .executeQuery("""SELECT SUM("pgsize") FROM "dbstat" WHERE name='$tableName'""")[1],
                rowCount = connection.executeQuery("SELECT COUNT(*) FROM $tableName")[1],
            )
        }

    private val sortableTree = SortableTree(tables)

    private val query = RSyntaxTextArea {
        syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_SQL
    }

    private val execute = Action(
        name = "Execute",
        description = "Execute (${if (SystemInfo.isMacOS) "⌘" else "Ctrl"} + Enter)",
        icon = FlatActionIcon("icons/bx-subdirectory-left.svg"),
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcutKeyMaskEx),
    ) {
        results.result = if (!query.text.isNullOrEmpty()) {
            try {
                connection.executeQuery(query.text)
                    .use { resultSet ->
                        val columnCount = resultSet.metaData.columnCount
                        val names = List(columnCount) { i ->
                            resultSet.metaData.getColumnName(i + 1)
                        }
                        val types = List(columnCount) { i ->
                            val isTimestamp = names[i].containsInOrder("tsmp", true)
                            if (isTimestamp) {
                                Date::class.java
                            } else {
                                val sqlType = resultSet.metaData.getColumnType(i + 1)
                                val jdbcType = JDBCType.valueOf(sqlType)
                                jdbcType.javaType
                            }
                        }

                        val data = resultSet.toList {
                            List(columnCount) { i ->
                                val value = resultSet.getObject(i + 1)
                                when {
                                    types[i] == Boolean::class.javaObjectType -> value == 1
                                    types[i] == Date::class.java && value is Number -> Date(value.toLong())
                                    else -> value
                                }
                            }
                        }

                        QueryResult.Success(names, types, data)
                    }
            } catch (e: Exception) {
                QueryResult.Error(e.message ?: "Error")
            }
        } else {
            QueryResult.Error("Enter a query in the text field above")
        }
    }

    private val queryPanel = JPanel(MigLayout("ins 0, fill")).apply {
        add(RTextScrollPane(query), "push, grow, wrap")
        add(JButton(execute), "ax right, wrap")
    }

    private val results = ResultsPanel()

    init {
        val ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcutKeyMaskEx)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ctrlEnter, "execute")
        query.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ctrlEnter, "execute")
        actionMap.put("execute", execute)
        query.actionMap.put("execute", execute)

        sortableTree.tree.attachPopupMenu { event ->
            val path = getClosestPathForLocation(event.x, event.y)
            when (val node = path?.lastPathComponent) {
                is Table -> JPopupMenu().apply {
                    add(
                        JMenuItem(
                            Action("SELECT * FROM ${node.name}") {
                                query.text = "SELECT * FROM ${node.name};"
                            },
                        ),
                    )
                }

                is Column -> JPopupMenu().apply {
                    val table = path.parentPath.lastPathComponent as Table
                    add(
                        JMenuItem(
                            Action("SELECT ${node.name} FROM ${table.name}") {
                                query.text = "SELECT ${node.name} FROM ${table.name}"
                            },
                        ),
                    )
                }

                else -> null
            }
        }

        add(
            HorizontalSplitPane(
                sortableTree.component,
                VerticalSplitPane(
                    queryPanel,
                    results,
                    resizeWeight = 0.2,
                    expandableSide = FlatSplitPane.ExpandableSide.both,
                ),
                resizeWeight = 0.1,
            ),
            "push, grow",
        )
    }

    override val icon: Icon? = null
}
