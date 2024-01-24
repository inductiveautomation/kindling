package io.github.inductiveautomation.kindling.statistics

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
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class GatewayBackup(path: Path) {
    private val zipFile = FileSystems.newFileSystem(path)
    private val root: Path = zipFile.rootDirectories.first()

    val info: Document = root.resolve(BACKUP_INFO).inputStream().use(XML_FACTORY::parse)

    val projectsDirectory: Path = root.resolve(PROJECTS)

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

    val ignitionConf: Properties by lazy {
        Properties((root.resolve(IGNITION_CONF)).inputStream())
    }

    val redundancyInfo: Properties by lazy {
        Properties(root.resolve(REDUNDANCY).inputStream(), Properties::loadFromXML)
    }

    companion object {
        private const val IDB = "db_backup_sqlite.idb"
        private const val BACKUP_INFO = "backupinfo.xml"
        private const val REDUNDANCY = "redundancy.xml"
        private const val IGNITION_CONF = "ignition.conf"
        private const val PROJECTS = "projects"
    }
}
