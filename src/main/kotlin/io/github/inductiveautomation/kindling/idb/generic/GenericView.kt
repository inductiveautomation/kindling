package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatSplitPane
import com.formdev.flatlaf.util.SystemInfo
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ButtonPanel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.javaType
import io.github.inductiveautomation.kindling.utils.menuShortcutKeyMaskEx
import io.github.inductiveautomation.kindling.utils.toList
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.event.KeyEvent
import java.sql.Connection
import java.sql.JDBCType
import java.sql.Timestamp
import java.util.Collections
import java.util.Enumeration
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

enum class TableComparator(
    val tooltip: String,
    val icon: FlatSVGIcon,
    val comparator: Comparator<Table>,
) : Comparator<Table> by comparator {
    ByNameAscending(
        tooltip = "Sort A-Z",
        icon = FlatSVGIcon("icons/bx-sort-a-z.svg"),
        comparator = compareBy(nullsFirst(AlphanumComparator(false))) { it.name },
    ),
    ByNameDescending(
        tooltip = "Sort Z-A",
        icon = FlatSVGIcon("icons/bx-sort-z-a.svg"),
        comparator = ByNameAscending.reversed(),
    ),
    BySizeAscending(
        tooltip = "Sort by Size",
        icon = FlatSVGIcon("icons/bx-sort-up.svg"),
        comparator = compareBy(Table::size),
    ),
    BySizeDescending(
        tooltip = "Sort by Size (descending)",
        icon = FlatSVGIcon("icons/bx-sort-down.svg"),
        comparator = BySizeAscending.reversed(),
    ),
}

class SortableTree(val tables: List<Table>) {
    var comparator = TableComparator.BySizeDescending
        set(value) {
            field = value
            root = sortedTreeNode()
            tree.model = DefaultTreeModel(root)
        }

    private fun sortedTreeNode() = object : TreeNode {
        private val sortedTables = tables.sortedWith(comparator)

        override fun getChildAt(childIndex: Int): TreeNode = sortedTables[childIndex]
        override fun getChildCount(): Int = sortedTables.size
        override fun getIndex(node: TreeNode): Int = sortedTables.indexOf(node)
        override fun children(): Enumeration<out TreeNode> = Collections.enumeration(sortedTables)
        override fun getParent(): TreeNode? = null
        override fun getAllowsChildren(): Boolean = true
        override fun isLeaf(): Boolean = false
    }

    var root: TreeNode = sortedTreeNode()

    val tree = DBMetaDataTree(DefaultTreeModel(root))

    private val sortActions: List<SortAction> = TableComparator.entries.map { tableComparator ->
        SortAction(tableComparator)
    }

    inner class SortAction(comparator: TableComparator) : Action(
        description = comparator.tooltip,
        icon = comparator.icon.asActionIcon(),
        selected = this@SortableTree.comparator == comparator,
        action = {
            this@SortableTree.comparator = comparator
            selected = true
        },
    ) {
        var comparator: TableComparator by actionValue("tableComparator", comparator)
    }

    private fun createSortButtons(): ButtonGroup = ButtonGroup().apply {
        for (sortAction in sortActions) {
            add(
                JToggleButton(
                    Action(
                        description = sortAction.description,
                        icon = sortAction.icon,
                        selected = sortAction.selected,
                    ) { e ->
                        sortAction.actionPerformed(e)
                    },
                ),
            )
        }
    }

    private val sortButtons = createSortButtons()

    val component = ButtonPanel(sortButtons).apply {
        add(FlatScrollPane(tree), "newline, push, grow")
    }
}

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
            )
        }

    private val sortableTree = SortableTree(tables)

    private val query = RSyntaxTextArea().apply {
        syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_SQL

        theme = Theme.currentValue

        Theme.addChangeListener { newTheme ->
            theme = newTheme
        }
    }

    private val execute = Action(
        name = "Execute",
        description = "Execute (${if (SystemInfo.isMacOS) "⌘" else "Ctrl"} + Enter)",
        icon = FlatSVGIcon("icons/bx-subdirectory-left.svg").asActionIcon(),
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
                            val timestamp =
                                TIMESTAMP_COLUMN_NAMES.any {
                                    resultSet.metaData.getColumnName(i + 1).contains(it, true)
                                }

                            if (timestamp) {
                                Timestamp::class.java
                            } else {
                                val sqlType = resultSet.metaData.getColumnType(i + 1)
                                val jdbcType = JDBCType.valueOf(sqlType)
                                jdbcType.javaType
                            }
                        }

                        val data = resultSet.toList {
                            List(columnCount) { i ->
                                // SQLite stores booleans as ints, we'll use actual booleans to make things easier
                                if (types[i] == Boolean::class.javaObjectType) {
                                    resultSet.getObject(i + 1) == 1
                                } else {
                                    resultSet.getObject(i + 1)
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

    companion object {
        private val TIMESTAMP_COLUMN_NAMES = setOf("timestamp", "timestmp", "t_stamp", "tstamp")
    }
}
