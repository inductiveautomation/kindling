package io.github.inductiveautomation.kindling.cache

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.inductiveautomation.ignition.gateway.history.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.history.ScanclassHistorySet
import com.jidesoft.swing.JideButton
import io.github.inductiveautomation.kindling.cache.model.AlarmJournalData
import io.github.inductiveautomation.kindling.cache.model.AlarmJournalSFGroup
import io.github.inductiveautomation.kindling.cache.model.AuditProfileData
import io.github.inductiveautomation.kindling.cache.model.ScriptedSFData
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import org.hsqldb.jdbc.JDBCDataSource
import org.intellij.lang.annotations.Language
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

private const val TRANSACTION_GROUP_DATA = "Transaction Group Data"

@OptIn(ExperimentalPathApi::class)
class CacheView(private val path: Path) : ToolPanel() {
    private val tempDirectory: Path = Files.createTempDirectory(path.nameWithoutExtension)

    private val dbName = when (path.extension) {
        "zip" -> run {
            LOGGER.debug("Exploding to {}", tempDirectory)
            ZipFile(path.toFile()).run {
                extractAll(tempDirectory.toString())
                fileHeaders.first { !it.isDirectory }.fileName.substringBeforeLast('.')
            }
        }

        in CacheViewer.extensions -> run {
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
    @Language("HSQLDB")
    private val schemaRecords = connection.prepareStatement(
        """
            SELECT
                schema.id,
                schema.signature,
                errors.message
            FROM datastore_schema schema
                LEFT JOIN datastore_errors errors
                ON errors.schemaid = schema.id
        """.trimMargin(),
    ).use { statement ->
        statement.executeQuery().toList { rs ->
            SchemaRow(
                rs.getInt("id"),
                rs.getString("signature"),
                rs.getString("message"),
            )
        }
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
    @Language("HSQLDB")
    private val data: List<CacheEntry> = connection.prepareStatement(
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
    ).use { statement ->
        statement.executeQuery().toList { resultSet ->
            CacheEntry(
                id = resultSet.getInt("id"),
                schemaId = resultSet.getInt("schemaid"),
                schemaName = resultSet.getString("name") ?: "null",
                timestamp = resultSet.getString("t_stamp"),
                attemptCount = resultSet.getInt("attemptcount"),
                dataCount = resultSet.getInt("data_count"),
            )
        }
    }

    private fun SchemaRecord.toDetail(): Detail {
        return Detail(
            title = name,
            body = errors.ifEmpty { listOf("No errors associated with this schema.") },
            details = mapOf(
                "ID" to id.toString(),
            ),
        )
    }

    private val details = DetailsPane()
    private val deserializedCache = mutableMapOf<Int, Detail>()
    private val model = CacheModel(data)
    private val table = ReifiedJXTable(model, CacheModel)
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

    private val mainSplitPane = JSplitPane(
        SwingConstants.HORIZONTAL,
        JSplitPane(
            SwingConstants.VERTICAL,
            FlatScrollPane(table),
            details,
        ).apply {
            resizeWeight = 0.5
        },
        FlatScrollPane(schemaList),
    ).apply {
        resizeWeight = 0.75
    }

    private fun Serializable.toDetail(): Detail = when (this) {
        is BasicHistoricalRecord -> toDetail()
        is ScanclassHistorySet -> toDetail()
        is AuditProfileData -> toDetail()
        is AlarmJournalData -> toDetail()
        is AlarmJournalSFGroup -> toDetail()
        is ScriptedSFData -> toDetail()
        is Array<*> -> {
            // 2D array
            if (firstOrNull()?.javaClass?.isArray == true) {
                Detail(
                    title = TRANSACTION_GROUP_DATA,
                    body = map { row ->
                        (row as Array<*>).contentToString()
                    },
                )
            } else {
                Detail(
                    title = "Java Array",
                    body = map(Any?::toString),
                )
            }
        }

        else -> Detail(
            title = this::class.java.name,
            message = toString(),
        )
    }

    /**
     * @throws ClassNotFoundException
     */
    private fun ByteArray.deserialize(): Serializable {
        return AliasingObjectInputStream(inputStream()) {
            put("com.inductiveautomation.ignition.gateway.audit.AuditProfileData", AuditProfileData::class.java)
            put("com.inductiveautomation.ignition.gateway.script.ialabs.IALabsDatasourceFunctions\$QuerySFData", ScriptedSFData::class.java)
//            put("com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal\$AlarmJournalSFData", AlarmJournalData::class.java)
//            put("com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal\$AlarmJournalSFGroup", AlarmJournalSFGroup::class.java)
        }.readObject() as Serializable
    }

    private fun ScanclassHistorySet.toDetail(): Detail {
        return Detail(
            title = this::class.java.simpleName,
            body = this.map { historicalTagValue ->
                buildString {
                    append(historicalTagValue.source.toStringFull())
                    append(", ")
                    append(historicalTagValue.typeClass.name)
                    append(", ")
                    append(historicalTagValue.value)
                    append(", ")
                    append(historicalTagValue.interpolationMode.name)
                    append(", ")
                    append(historicalTagValue.timestampSource.name)
                }
            },
            details = mapOf(
                "gatewayName" to gatewayName,
                "provider" to providerName,
                "setName" to setName,
                "execRate" to execRate.toString(),
                "execTime" to executionTime.time.toString(), // TODO date format?
            ),
        )
    }

    private fun BasicHistoricalRecord.toDetail(): Detail {
        return Detail(
            title = "BasicHistoricalRecord",
            message = "INSERT INTO $tablename",
            body = columns.map { column ->
                buildString {
                    append(column.name).append(": ")
                    (0..dataCount).joinTo(buffer = this, prefix = "(", postfix = ")") { row ->
                        column.getValue(row).toString()
                    }
                }
            },
            details = mapOf(
                "quoteColumnNames" to quoteColumnNames().toString(),
            ),
        )
    }

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
        val id = table.model[table.selectedRow, CacheModel.Id]
        val raw = queryForData(id).deserialize()
        val originalData = raw as Array<*>
        val cols = (originalData[0] as Array<*>).size
        val rows = originalData.size
        val data = Array(cols) { j ->
            Array(rows) { i ->
                (originalData[i] as Array<*>)[j]
            }
        }

        // Get table name and column names with schemaName
        val schemaName = table.model[table.selectedRow, CacheModel.SchemaName]
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
                                val deserialized = bytes.deserialize()
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
                table.model = CacheModel(filteredData)
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

object CacheViewer : Tool {
    override val title = "Cache Dump"
    override val description = "S&F Cache data/script files"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    override val extensions = listOf("data", "script", "zip")
    override fun open(path: Path): ToolPanel = CacheView(path)
}
