package io.github.inductiveautomation.kindling.statistics

import io.github.inductiveautomation.kindling.resources.ResourceCollection
import io.github.inductiveautomation.kindling.utils.Properties
import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.XML_FACTORY
import io.github.inductiveautomation.kindling.utils.parse
import io.github.inductiveautomation.kindling.utils.transferTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Document
import java.nio.file.FileSystems
import java.nio.file.Path
import java.sql.Connection
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream

sealed class GatewayBackup(protected val root: Path) {
    val info: Document = root.resolve(BACKUP_INFO).inputStream().use(XML_FACTORY::parse)

    val version: String
        get() = info.getElementsByTagName("version").item(0).textContent

    val edition: String?
        get() = info.getElementsByTagName("edition").item(0)?.textContent?.takeUnless(String::isEmpty)

    val projectsDirectory: Path = root.resolve(PROJECTS)

    val projects: Map<String, ResourceCollection>? by lazy {
        projectsDirectory.takeIf { it.exists() }?.let(ResourceCollection::ofProjects)
    }

    val ignitionConf: Properties by lazy {
        Properties((root.resolve(IGNITION_CONF)).inputStream())
    }

    val redundancyInfo: Properties by lazy {
        Properties(root.resolve(REDUNDANCY).inputStream(), Properties::loadFromXML)
    }

    /**
     * A 7.9 - 8.1 gateway backup, with configuration stored in an embedded SQLite database.
     */
    class InternalDatabase internal constructor(root: Path) : GatewayBackup(root) {
        private val tempFile: Path = createTempFile("gwbk-stats", "idb")

        // eagerly copy out the IDB, since we're always building the statistics view anyways
        private val dbCopyJob =
            CoroutineScope(Dispatchers.IO).launch {
                root.resolve(IDB).inputStream() transferTo tempFile.outputStream()
            }

        val configDb: Connection by lazy {
            // ensure the file copy is complete
            runBlocking { dbCopyJob.join() }

            SQLiteConnection(tempFile)
        }
    }

    /**
     * An 8.3+ gateway backup, with configuration stored as resource collections (deployment modes) under `config/`.
     */
    class Filesystem internal constructor(root: Path) : GatewayBackup(root) {
        val configDirectory: Path = root / CONFIG

        val config: Map<String, ResourceCollection> by lazy {
            ResourceCollection.ofConfig(configDirectory / RESOURCES)
        }

        val core: ResourceCollection
            get() = config.getValue(ResourceCollection.CORE)

        val local: ResourceCollection?
            get() = config[ResourceCollection.LOCAL]
    }

    companion object {
        private const val IDB = "db_backup_sqlite.idb"
        private const val BACKUP_INFO = "backupinfo.xml"
        private const val REDUNDANCY = "redundancy.xml"
        private const val IGNITION_CONF = "ignition.conf"
        private const val PROJECTS = "projects"
        private const val CONFIG = "config"
        private const val RESOURCES = "resources"

        operator fun invoke(path: Path): GatewayBackup {
            val root = FileSystems.newFileSystem(path).rootDirectories.first()
            return if ((root / CONFIG).isDirectory()) Filesystem(root) else InternalDatabase(root)
        }
    }
}
