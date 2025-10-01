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
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.menuShortcutKeyMaskEx
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.DefaultCairoConfiguration
import io.questdb.cairo.security.AllowAllSecurityContext
import io.questdb.griffin.SqlExecutionContext
import io.questdb.griffin.SqlExecutionContextImpl
import java.awt.event.KeyEvent
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane

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

    private val engine = CairoEngine(DefaultCairoConfiguration(dbRootPath.absolutePathString())).apply {
        metadataCache.onStartupAsyncHydrator()
    }

    private val sqlContext: SqlExecutionContext = SqlExecutionContextImpl(engine, 1).with(AllowAllSecurityContext.INSTANCE, null)

    private val pageLabel: JLabel

    @Suppress("SqlResolve")
    val tables: List<Table> = context(sqlContext) {
        engine.select("SELECT table_name FROM tables();") { tableRec ->
            val name = tableRec.get<String>(0)!!

            val size = engine.select(
                "SELECT diskSize FROM table_storage() WHERE tableName = '$name';",
            ) {storageRec ->
                storageRec.get<Long>(0)
            }.singleOrNull() ?: 0L

            val cols = engine.select("SHOW COLUMNS FROM \"$name\";") { rec ->
                Column(
                    name = rec["column"]!!,
                    type = rec["type"]!!,
                    notNull = false,
                    defaultValue = null,
                    primaryKey = rec["upsertKey"]!!,
                    hidden = false,
                    _parent = { sortableTree.root }
                )
            }

            Table(
                name = name,
                columns = cols,
                _parent = { sortableTree.root },
                size = size,
            )
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
                val fact = engine.select(query.text, sqlContext)
                val cur = fact.getCursor(sqlContext)

                val names = List(fact.metadata.columnCount) {
                    fact.metadata.getColumnName(it)
                }

                val types = List(fact.metadata.columnCount) {
                    fact.metadata.getColumnClass(it)
                }.filterNotNull()

                val data: List<List<*>> = buildList {
                    context(fact.metadata) {
                        val rec = cur.record
                        while (cur.hasNext()) {
                            add(
                                List(names.size) { rec.get<Any?>(it) }
                            )
                        }
                    }
                }

                QueryResult.Success(names, types.map { it.javaObjectType }, data)
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

        engine.close()

        tempDirectory.deleteRecursively()
    }

    // constants
    companion object {
        private val LOGGER = getLogger<QuestDbView>()
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
