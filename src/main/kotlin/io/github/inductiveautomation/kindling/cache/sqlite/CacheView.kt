package io.github.inductiveautomation.kindling.cache.sqlite

import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.deserializeStoreAndForward
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toDetail
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import io.github.inductiveautomation.kindling.utils.toHumanReadableBinary
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.transferTo
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.walk
import org.sqlite.SQLiteConnection

class CacheView private constructor(connections: List<Connection>) : ToolPanel() {
    private val cacheData = connections.flatMap { conn ->
        conn.executeQuery(CACHE_DATA_QUERY).toList { rs ->
            val id: Int = rs["id"]

            IdbCacheEntry(
                id = id,
                dataSize = rs["data_length"],
                timestamp = rs["timestamp"],
                attemptCount = rs["attempt_count"],
                dataCount = rs["data_count"],
                flavorId = rs["flavor_id"],
                flavorName = rs["flavor"],
                quarantineId = rs["quarantine_id"],
                reason = rs["reason"],
                quarantineFlavorId = rs["quarantine_flavor_id"],
                getData = {
                    conn.prepareStatement(GET_DATA_QUERY).run {
                        setInt(1, id)
                        executeQuery().toList { dataResult ->
                            dataResult.getBytes("data")
                        }.single()
                    }
                }
            )
        }
    }

    private val cacheTable = ReifiedJXTable(
        ReifiedListTableModel(cacheData, IdbCacheColumns),
    ).apply {
        setDefaultRenderer<ByteArray>(
            getText = {
                if (it != null) {
                    "${it.size.toLong().toFileSizeLabel()} BLOB"
                } else {
                    ""
                }
            },
            getTooltip = { "Export to CSV to view full data (b64 encoded)" },
        )
    }

    private val details = DetailsPane()

    val dataFlavorFilterList = FilterList().apply {
        setModel(
            FilterModel(
                cacheData.groupingBy(IdbCacheEntry::flavorName).eachCount(),
                sortKey = { it }
            )
        )
    }

    override val icon = null

    init {
        name = "S&F Cache View"

        add(
            HorizontalSplitPane(
                left = VerticalSplitPane(
                    top = FlatScrollPane(cacheTable),
                    bottom = details,
                ),
                right = FlatScrollPane(dataFlavorFilterList)
            ),
            "push, grow, span",
        )

        cacheTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                details.events = cacheTable.selectedRowIndices().map { index ->
                    val modelIndex = cacheTable.convertRowIndexToModel(index)
                    val entry = cacheTable.model[modelIndex]

                    try {
                        val deserialized = entry.data.deserializeStoreAndForward()
                        deserialized.toDetail()
                    } catch (_: Exception) {
                        // It's not serialized with a class in the public API, or some other problem;
                        // give up, and try to just dump the serialized data in a friendlier format
                        val serializationDumper = deser.SerializationDumper(entry.data)

                        Detail(
                            title = "Serialization dump of ${entry.data.size} bytes:",
                            body = try {
                                serializationDumper.parseStream().lines()
                            } catch (_: Exception) {
                                entry.data.inputStream().use {
                                    it.toHumanReadableBinary().split("\n")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val CACHE_DATA_QUERY = """
            SELECT
                d.id,
                OCTET_LENGTH(d.data) as 'data_length',
                d.timestamp,
                d.attempt_count,
                d.data_count,
                d.flavor_id,
                f.flavor,
                d.quarantine_id,
                q.reason,
                q.flavor_id as 'quarantine_flavor_id'
            FROM persistent_data d
            LEFT JOIN persistent_flavors f ON f.id = d.flavor_id
            LEFT JOIN quarantine_info q ON q.id = d.quarantine_id
        """

        private const val GET_DATA_QUERY = "SELECT data from persistent_data WHERE id = ?"

        fun fromZip(path: Path): CacheView {
            val connections = FileSystems.newFileSystem(path).use {  fs ->
                val tempDir = Files.createTempDirectory("kindling")

                fs.rootDirectories.first().walk().mapNotNull {
                    if (it.extension == "idb") {
                        val tempFile = Files.createTempFile(tempDir, "kindling", "cache")
                        it.inputStream() transferTo tempFile.outputStream()
                        SQLiteConnection(tempFile, journalEnabled = true)
                    } else {
                        null
                    }
                }.toList()
            }

            return CacheView(connections)
        }

        fun fromConnection(connection: Connection): CacheView {
            return CacheView(listOf(connection))
        }

        fun fromIdb(idbPath: Path) = fromConnection(SQLiteConnection(idbPath, journalEnabled = true))
    }
}