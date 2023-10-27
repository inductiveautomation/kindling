package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagConfig
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.awt.Font
import java.sql.Connection
import javax.swing.JScrollPane
import javax.swing.JTextArea

class TagConfigView(connection: Connection) : ToolPanel() {

    private val errs = mutableListOf<Exception>()

    private val tagRecordData = connection.prepareStatement("SELECT * FROM TAGCONFIG").executeQuery().toList { rs ->
        try {
            TagRecord(
                id = rs.getString(1),
                providerId = rs.getInt(2),
                folderId = rs.getString(3),
                config = JSON.decodeFromString(rs.getString(4)),
                rank = rs.getInt(5),
                name = rs.getString(6), // Null Records in IDB - Corruption?
            )
        } catch (e: NullPointerException) {
            null
        } catch (e: SerializationException) {
            errs.add(e)
            e.printStackTrace()
            null
        }
    }

    override val icon = null

    // private val unknownKeyRegex = """Encountered an unknown key '(?<unknownKey>.*)' at path""".toRegex()

    private val nullPropRegex = """\b\w+=null\b, """.toRegex()

    init {
        val tagConfigs = tagRecordData.mapNotNull { record ->
            record?.config
        }

        val textToDisplay = nullPropRegex.replace(tagConfigs.joinToString("\n\n"), "")

        val myLabel = JTextArea(textToDisplay).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        add(JScrollPane(myLabel), "grow")
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
    }
}

data class TagRecord(
    val id: String,
    val providerId: Int,
    val folderId: String?,
    val config: TagConfig,
    val rank: Int,
    val name: String,
)
