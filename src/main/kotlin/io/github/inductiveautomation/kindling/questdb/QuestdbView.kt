package io.github.inductiveautomation.kindling.questdb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.questdb.model.QuestdbAnnotationsColumns
import io.github.inductiveautomation.kindling.questdb.model.QuestdbAnnotationsEntry
import io.github.inductiveautomation.kindling.questdb.model.QuestdbDatapointsColumns
import io.github.inductiveautomation.kindling.questdb.model.QuestdbDatapointsEntry
import io.github.inductiveautomation.kindling.questdb.model.QuestdbDefinitionsColumns
import io.github.inductiveautomation.kindling.questdb.model.QuestdbDefinitionsEntry
import io.github.inductiveautomation.kindling.questdb.model.QuestdbMetadataColumns
import io.github.inductiveautomation.kindling.questdb.model.QuestdbMetadataEntry
import io.github.inductiveautomation.kindling.questdb.model.QuestdbTableColumns
import io.github.inductiveautomation.kindling.questdb.model.QuestdbTableEntry
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.toList
import io.questdb.ServerMain
import java.awt.Component
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel
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
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTable
import org.postgresql.ds.PGSimpleDataSource

@OptIn(ExperimentalPathApi::class)
class QuestdbView(path: Path) : ToolPanel() {
    override val icon: Icon = QuestdbViewer.icon

    // Copy the zip data over to the temp directory
    // temp dir
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

        val httpPort = ServerSocket(0).use { it.localPort } // web console port
        val config = """
            pg.net.bind.to=0.0.0.0:$pgPort
            http.net.bind.to=0.0.0.0:$httpPort
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

    // pagination
    private var selectedTable: String = ""
    private var currentPage: Int = 1
    private var maxPages: Int = 0
    private val pageLabel: JLabel

    // data
    private lateinit var tables: List<QuestdbTableEntry>

    private var tableModel: ReifiedListTableModel<QuestdbTableEntry>
    private var tableSelectionTable: ReifiedJXTable<ReifiedListTableModel<QuestdbTableEntry>>
    private val queryDisplayTable = JXTable()
    private var verticalSplitPane: Component

    init {
        name = path.name
        toolTipText = path.toString()

        // get tables and store in class tables
        getQuestDBTables()

        tableModel = ReifiedListTableModel(tables, QuestdbTableColumns)

        tableSelectionTable = ReifiedJXTable(tableModel).apply {
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectionModel.addListSelectionListener {
                val selection = tableSelectionTable.selectionModel.selectedIndices.firstOrNull() ?: return@addListSelectionListener
                val tableName = tableSelectionTable.model[selection].tableName
                selectedTable = tableName
                currentPage = 1
                setQueryTableModel(selectedTable)
                if (!queryDisplayTable.isVisible) setQueryTableVisibility(true)
            }
        }

        setQueryTableVisibility(false)

        pageLabel = JLabel("")

        val bottomPanel = JPanel(MigLayout("insets 0", "[left][center, grow][right]")).apply {
            add(FlatScrollPane(queryDisplayTable), "wrap, span, push, grow")

            add(JButton("Previous").apply {
                addActionListener { prevPage() }
            })

            add(pageLabel, "pushx, align center")

            add(JButton("Next").apply {
                addActionListener { nextPage() }
            })
        }

        verticalSplitPane = VerticalSplitPane(
            FlatScrollPane(tableSelectionTable),
            bottomPanel,
        )

        add(verticalSplitPane, "push, grow")

        LOGGER.debug(tables.toString())
    }

    // quest queries
    private fun getQuestDBTables() {
        try {
            @Suppress("SqlResolve")
            tables = connection.executeQuery("SELECT * FROM tables()")
                .toList { rs -> QuestdbTableEntry(rs) }
        } catch (e: Exception) {
            LOGGER.error("Failed to getQuestDBTables(): $e")
        }
    }

    private fun queryQuestDB(tableName: String): TableModel {
        maxPages = getMaxPages(tableName)
        setPageLabelText()
        try {
            val data = connection.executeQuery(
                """SELECT * FROM $tableName
                  LIMIT ${(currentPage - 1) * RESULTS_PER_PAGE},${currentPage * RESULTS_PER_PAGE}
                  """
            )
            if (tableName.contains("datapoints")) {
                return ReifiedListTableModel(
                    data.toList { rs ->
                        QuestdbDatapointsEntry(rs)
                    },
                    QuestdbDatapointsColumns,
                )
            } else if (tableName.contains("annotations")) {
                return ReifiedListTableModel(
                    data.toList { rs ->
                        QuestdbAnnotationsEntry(rs)
                    },
                    QuestdbAnnotationsColumns,
                )

            } else if (tableName.contains("definitions")) {
                return ReifiedListTableModel(
                    data.toList { rs ->
                        QuestdbDefinitionsEntry(rs)
                    },
                    QuestdbDefinitionsColumns,
                )
            } else if (tableName.contains("metadata")) {
                return ReifiedListTableModel(
                    data.toList { rs ->
                        QuestdbMetadataEntry(rs)
                    },
                    QuestdbMetadataColumns,
                )
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to queryQuestDB(): $e")
        }
        return DefaultTableModel()
    }

    // pagination
    private fun getMaxPages(tableName: String): Int {
        try {
            val rs = connection.executeQuery("SELECT COUNT(*) FROM $tableName")
            if (rs.next()) {
                val count = rs.getInt(1)
                val maxPages = (count + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
                return if (maxPages > 0) maxPages else 1
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to getMaxPages(): $e")
        }
        return 1
    }

    private fun nextPage() {
        if (currentPage < maxPages) {
            currentPage++
        }
        setQueryTableModel(selectedTable)
    }

    private fun prevPage() {
        if (currentPage > 1) {
            currentPage--
        }
        setQueryTableModel(selectedTable)
    }

    private fun setPageLabelText() {
        pageLabel.text = "${currentPage}/${maxPages}"
    }

    // query table component
    private fun setQueryTableVisibility(visibility: Boolean) {
        queryDisplayTable.isVisible = visibility
        queryDisplayTable.tableHeader.isVisible = visibility
    }

    private fun setQueryTableModel(tableName: String) {
        val tableModel = queryQuestDB(tableName)
        queryDisplayTable.model = tableModel
    }

    override fun removeNotify() {
        super.removeNotify()
        LOGGER.debug("Closing resources for {}", tempDirectory)

        try {
            connection.close()
        } catch (_: Exception) {}

        try {
            LOGGER.debug("Shutting down QuestDB server on port {}", pgPort)
            server.close()
        } catch (e: Exception) {
            LOGGER.error("Failed to close QuestDB server", e)
        }

        tempDirectory.deleteRecursively()
    }

    // constants
    companion object {
        private val LOGGER = getLogger<QuestdbView>()
        private const val PG_HOST = "localhost"
        private const val PG_USER = "admin"
        private const val PG_PASS = "quest"
        private const val RESULTS_PER_PAGE = 50
    }
}

data object QuestdbViewer : MultiTool {
    override val serialKey = "questdb-viewer"
    override val title = "QuestDB Viewer"
    override val description = "QuestDB Export (.zip)"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val filter = FileFilter(description, "zip")
    override fun open(path: Path): ToolPanel = QuestdbView(path)
    override fun open(paths: List<Path>): ToolPanel = open(paths.first())
}
