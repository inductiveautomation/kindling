package io.github.inductiveautomation.kindling.questdb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatSplitPane
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.db.Column
import io.github.inductiveautomation.kindling.core.db.QueryResult
import io.github.inductiveautomation.kindling.core.db.ResultsPanel
import io.github.inductiveautomation.kindling.core.db.SortableTree
import io.github.inductiveautomation.kindling.core.db.Table
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.javaType
import io.github.inductiveautomation.kindling.utils.menuShortcutKeyMaskEx
import io.github.inductiveautomation.kindling.utils.toList
import io.questdb.ServerMain
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import org.postgresql.ds.PGSimpleDataSource
import java.awt.event.KeyEvent
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.JDBCType
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class QuestDbView(path: Path) : ToolPanel() {
    override val icon: Icon = QuestDbViewer.icon

    private val tempDirectory: Path = Files.createTempDirectory(path.nameWithoutExtension).apply {
        if (path.extension.lowercase() == "zip") {
            LOGGER.debug("Exploding zip to {}", this)
            FileSystems.newFileSystem(path).use { zip ->
                zip.rootDirectories.first().copyToRecursively(
                    target = this,
                    followLinks = false,
                )
            }
        } else {
            throw ToolOpeningException(".${path.extension} files not supported. Please provide a .zip file.")
        }
    }

    private val dbRootPath: Path = run {
        val foundDbPath = tempDirectory.walk().filter { p: Path ->
            p.fileName.toString() == "_tab_index.d"
        }.firstOrNull()?.parent
            ?: throw ToolOpeningException("Unable to find a valid QuestDB root (_tab_index.d) inside the zip file.")

        LOGGER.debug(foundDbPath.toString())

        if (foundDbPath == tempDirectory) {
            throw ToolOpeningException(
                "Unsupported Zip Structure: The zip file must contain a parent folder for the database files. " +
                    "Please zip the folder that contains the 'db' directory.",
            )
        }

        LOGGER.debug("dbRoot found at: {}", foundDbPath)

        foundDbPath
    }

    // Set up the server
    private val pgPort: Int = ServerSocket(0).use { it.localPort }.also {
        LOGGER.debug("Acquired port {} for QuestDB instance", it)
    }

    init {
        // Create conf file
        val confDir = dbRootPath.parent / "conf"
        confDir.createDirectory()
        val confFile = confDir / "server.conf"

        val config = """
            pg.net.bind.to=127.0.0.1:$pgPort
            pg.net.connection.timeout=0
        """.trimIndent()

        confFile.writeText(config)
    }

    private val server: ServerMain = ServerMain.create(dbRootPath.parent.toString()).apply {
        start()
        Socket().use { it.connect(InetSocketAddress(PG_HOST, pgPort), 15000) }
    }

    // Create a connection to the server
    private var connection: Connection = PGSimpleDataSource().run {
        databaseName = "qdb"
        user = PG_USER
        password = PG_PASS
        portNumbers = intArrayOf(pgPort)

        connection
    }

    private val pageLabel: JLabel

    @Suppress("SqlResolve")
    val tables: List<Table> = connection
        .executeQuery("SELECT table_name FROM tables();")
        .toList { resultSet ->
            resultSet.getString("table_name")
        }.mapNotNull { tableName ->
            try {
                val columns = connection
                    .executeQuery("""SHOW COLUMNS FROM "$tableName";""")
                    .toList { rs ->
                        Column(
                            name = rs.getString("column"),
                            type = rs.getString("type"),
                            notNull = false,
                            defaultValue = null,
                            primaryKey = rs.getBoolean("upsertKey"),
                            hidden = false,
                            _parent = { sortableTree.root },
                        )
                    }
                val size: Long = connection.executeQuery("SELECT diskSize FROM table_storage() WHERE tableName = '$tableName'").use { rs ->
                    if (rs.next()) rs.getLong("diskSize") else 0L
                }
                Table(
                    name = tableName,
                    _parent = { sortableTree.root },
                    columns = columns,
                    size = size,
                )
            } catch (e: Exception) {
                LOGGER.error("Warning: Could not process table '$tableName'. Error: ${e.message}")
                null
            }
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
                            val sqlType = resultSet.metaData.getColumnType(i + 1)
                            val jdbcType = JDBCType.valueOf(sqlType)
                            jdbcType.javaType
                        }

                        val data = resultSet.toList {
                            List(columnCount) { i ->
                                resultSet.getObject(i + 1)
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

    private val results = ResultsPanel()
    private val queryPanel = JPanel(MigLayout("ins 0, fill")).apply {
        add(RTextScrollPane(query), "push, grow, wrap")
        add(JButton(execute), "ax right, wrap")
    }

    init {
        name = path.name
        toolTipText = path.toString()

        val ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcutKeyMaskEx)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ctrlEnter, "execute")
        query.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ctrlEnter, "execute")
        actionMap.put("execute", execute)
        query.actionMap.put("execute", execute)

        pageLabel = JLabel("")

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

        LOGGER.debug(tables.toString())
    }

    override fun removeNotify() {
        super.removeNotify()
        LOGGER.debug("Closing resources for {}", tempDirectory)

        val ex = runCatching {
            connection.close()
            LOGGER.debug("Shutting down QuestDB server on port {}", pgPort)
            server.close()
        }.exceptionOrNull()

        if (ex != null) {
            LOGGER.error("Failed to release resource", ex)
        }

        tempDirectory.deleteRecursively()
    }

    // constants
    companion object {
        private val LOGGER = getLogger<QuestDbView>()
        private const val PG_HOST = "localhost"
        private const val PG_USER = "admin"
        private const val PG_PASS = "quest"
    }
}

data object QuestDbViewer : MultiTool {
    override val serialKey = "questdb-viewer"
    override val title = "QuestDB Viewer"
    override val description = "QuestDB Export (.zip)"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val filter = FileFilter(description, "zip")
    override fun open(path: Path): ToolPanel = QuestDbView(path)
    override fun open(paths: List<Path>): ToolPanel = open(paths.first())
}
