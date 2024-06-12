package io.github.inductiveautomation.kindling.cache

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.jidesoft.swing.JideButton
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.TRANSACTION_GROUP_DATA
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.deserializeStoreAndForward
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toDetail
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hsqldb.jdbc.JDBCDataSource
import org.intellij.lang.annotations.Language
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.table.DefaultTableModel
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@OptIn(ExperimentalPathApi::class)
class CacheView(path: Path) : ToolPanel() {
    private val tempDirectory: Path = Files.createTempDirectory(path.nameWithoutExtension)

    private val dbName = when (path.extension) {
        "zip" -> {
            LOGGER.debug("Exploding to {}", tempDirectory)
            var dbName: String? = null
            FileSystems.newFileSystem(path).use { zip ->
                for (directory in zip.rootDirectories) {
                    directory.copyToRecursively(
                        target = tempDirectory,
                        copyAction = { source: Path, target: Path ->
                            target.parent?.createDirectories()
                            if (source.extension in cacheFileExtensions) {
                                source.copyToIgnoringExistingDirectory(target, false)
                            }

                            dbName = "${target.nameWithoutExtension}/${target.nameWithoutExtension}"

                            CopyActionResult.CONTINUE
                        },
                        followLinks = false,
                    )
                }
            }
            dbName ?: throw ToolOpeningException("Unable to find an HSQL DB inside zip file")
        }

        in CacheViewer.extensions -> {
            path.parent.copyToRecursively(
                target = tempDirectory,
                followLinks = false,
                copyAction = { source: Path, target: Path ->
                    if (source.extension in cacheFileExtensions) {
                        source.copyToIgnoringExistingDirectory(target, false)
                    }
                    CopyActionResult.CONTINUE
                },
            )
            path.nameWithoutExtension
        }

        else -> throw ToolOpeningException(".${path.extension} files not supported.")
    }.also { dbName ->
        LOGGER.trace("dbName: $dbName")
    }

    private val connection = JDBCDataSource().apply {
        setUrl(
            buildString {
                append("jdbc:hsqldb:file:")
                append(tempDirectory).append("/").append(dbName).append(";")
                append("create=").append(false).append(";")
                append("shutdown=").append(true).append(";")
            }.also { url ->
                LOGGER.trace("JDBC URL: {}", url)
            },
        )
        user = "SA"
        setPassword("dstorepass")
    }.connection

    override fun removeNotify() = super.removeNotify().also {
        connection.close()
    }

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val dataQuery: PreparedStatement = connection.prepareStatement(
        "SELECT data FROM datastore_data WHERE id = ?",
    )

    private fun queryForData(id: Int): ByteArray {
        dataQuery.apply {
            setInt(1, id)
            executeQuery().use { resultSet ->
                resultSet.next()
                val bytes = resultSet.getBytes(1)
                return GZIPInputStream(bytes.inputStream()).readAllBytes()
            }
        }
    }

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    private val schemaRecords = connection.executeQuery(
        """
        SELECT
            schema.id,
            schema.signature,
            errors.message
        FROM datastore_schema schema
            LEFT JOIN datastore_errors errors
            ON errors.schemaid = schema.id
        """.trimMargin(),
    )
        .toList { rs ->
            SchemaRow(
                rs["id"],
                rs["signature"],
                rs["message"],
            )
        }
        .groupBy(SchemaRow::id)
        .map { (id, rows) ->
            SchemaRecord(
                id = id,
                name = rows.firstNotNullOf(SchemaRow::signature),
                errors = rows.mapNotNull(SchemaRow::message),
            )
        }

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    private val data: List<CacheEntry> = connection.executeQuery(
        """
        SELECT
            data.id,
            data.schemaid,
            schema.signature AS name,
            data.t_stamp,
            data.attemptcount,
            data.data_count
        FROM
            datastore_data data
            LEFT JOIN datastore_schema schema ON schema.id = data.schemaid
        """.trimIndent(),
    ).toList { resultSet ->
        CacheEntry(
            id = resultSet["id"],
            schemaId = resultSet["schemaid"],
            schemaName = resultSet["name"] ?: "null",
            timestamp = resultSet["t_stamp"],
            attemptCount = resultSet["attemptcount"],
            dataCount = resultSet["data_count"],
        )
    }

    private fun SchemaRecord.toDetail() = Detail(
        title = name,
        body = errors.ifEmpty { listOf("No errors associated with this schema.") },
        details = mapOf(
            "ID" to id.toString(),
        ),
    )

    private val details = DetailsPane()
    private val deserializedCache = mutableMapOf<Int, Detail>()
    private val model = ReifiedListTableModel(data, CacheColumns)
    private val table = ReifiedJXTable(model)
    private val schemaList = SchemaFilterList(schemaRecords)

    private val settingsMenu = FlatPopupMenu().apply {
        add(
            Action("Show Schema Records") {
                val isVisible = mainSplitPane.bottomComponent.isVisible
                mainSplitPane.bottomComponent.isVisible = !isVisible
                if (!isVisible) {
                    mainSplitPane.setDividerLocation(0.75)
                }
            },
        )
    }

    private val settings = JideButton(FlatSVGIcon("icons/bx-cog.svg")).apply {
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    settingsMenu.show(this@apply, e.x, e.y)
                }
            },
        )
    }

    private val mainSplitPane = HorizontalSplitPane(
        VerticalSplitPane(
            FlatScrollPane(table),
            details,
        ),
        FlatScrollPane(schemaList),
        resizeWeight = 0.75,
    )

    private val columnNameRegex = """(?<tableName>.*)\{(?<columnsString>.*)}""".toRegex()

    private val openArrayFrame = Action(
        name = TRANSACTION_GROUP_DATA,
        icon = FlatSVGIcon("icons/bx-detail.svg"),
    ) {
        /*
         * A few assumptions are made:
         * 1. The currently selected table row matches the entry in the Details pane.
         * 2. There is only row selected
         *
         * We need the ID to get the table data and the schemaName to get the table columns and table name
         */
        val id = table.model[table.selectedRow, CacheColumns.Id]
        val raw = queryForData(id).deserializeStoreAndForward()
        val originalData = raw as Array<*>
        val cols = (originalData[0] as Array<*>).size
        val rows = originalData.size
        val data =
            Array(cols) { j ->
                Array(rows) { i ->
                    (originalData[i] as Array<*>)[j]
                }
            }

        // Get table name and column names with schemaName
        val schemaName = table.model[table.selectedRow, CacheColumns.SchemaName]
        val matcher = columnNameRegex.find(schemaName) ?: return@Action
        val tableName by matcher.groups
        val columnsString by matcher.groups
        val columns = columnsString.value.split(",").toTypedArray()

        // Use data and columns to create a simple table model
        val model = DefaultTableModel(data, columns)

        jFrame(tableName.value, 900, 500) {
            contentPane = FlatScrollPane(ReifiedJXTable(model))
        }
    }

    init {
        name = path.name
        toolTipText = path.toString()

        add(JLabel("${data.size} ${if (data.size == 1) "entry" else "entries"}"))
        add(settings, "right, wrap")

        add(mainSplitPane, "push, grow, span")

        schemaList.selectionModel.addListSelectionListener {
            details.events = schemaList.selectedValuesList.filterIsInstance<SchemaRecord>().map { it.toDetail() }
        }

        details.actions.add(openArrayFrame)

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                details.events = table.selectedRowIndices()
                    .map { index -> data[index].id }
                    .map { id ->
                        deserializedCache.getOrPut(id) {
                            val bytes = queryForData(id)
                            try {
                                val deserialized = bytes.deserializeStoreAndForward()
                                deserialized.toDetail()
                            } catch (e: Exception) {
                                // It's not serialized with a class in the public API, or some other problem;
                                // give up, and try to just dump the serialized data in a friendlier format
                                val serializationDumper = deser.SerializationDumper(bytes)

                                Detail(
                                    title = "Serialization dump of ${bytes.size} bytes:",
                                    body = serializationDumper.parseStream().lines(),
                                )
                            }
                        }
                    }
                openArrayFrame.isEnabled = details.events.singleOrNull()?.title == TRANSACTION_GROUP_DATA
            }
        }

        schemaList.checkBoxListSelectionModel.addListSelectionListener {
            updateData()
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredData = data.filter { entry ->
                schemaRecords.find { it.id == entry.schemaId } in schemaList.checkBoxListSelectedValues
            }
            EDT_SCOPE.launch {
                table.model = ReifiedListTableModel(filteredData, CacheColumns)
            }
        }
    }

    override val icon: Icon = CacheViewer.icon

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
        val LOGGER = getLogger<CacheView>()
        val cacheFileExtensions = listOf("data", "script", "log", "backup", "properties")
    }
}

data object CacheViewer : Tool {
    override val serialKey = "sf-cache"
    override val title = "Store & Forward Cache"
    override val description = "S&F Cache (.data, .script, .zip)"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    internal val extensions = arrayOf("data", "script", "zip")
    override val filter = FileFilter(description, *extensions)

    override fun open(path: Path): ToolPanel = CacheView(path)
}
