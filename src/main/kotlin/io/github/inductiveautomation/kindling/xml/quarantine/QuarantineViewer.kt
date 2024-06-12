package io.github.inductiveautomation.kindling.xml.quarantine

import deser.SerializationDumper
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.XML_FACTORY
import io.github.inductiveautomation.kindling.utils.deserializeStoreAndForward
import io.github.inductiveautomation.kindling.utils.parse
import io.github.inductiveautomation.kindling.utils.toDetail
import io.github.inductiveautomation.kindling.xml.XmlTool
import net.miginfocom.swing.MigLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
import com.inductiveautomation.ignition.common.Base64 as IaBase64

internal class QuarantineViewer(data: List<QuarantineRow>) : JPanel(MigLayout("ins 6, fill, hidemode 3")) {
    private val list = JList(Array(data.size) { i -> i + 1 }).apply {
        selectionMode = MULTIPLE_INTERVAL_SELECTION
    }

    private val detailsPane = DetailsPane()

    init {
        list.addListSelectionListener {
            detailsPane.events = list.selectedIndices.map { i ->
                data[i].detail
            }
        }

        add(
            HorizontalSplitPane(
                FlatScrollPane(list),
                detailsPane,
                0.1,
            ),
            "push, grow",
        )
    }

    internal data class QuarantineRow(
        val b64data: String,
    ) {
        private val binaryData: ByteArray by lazy {
            IaBase64.decodeAndGunzip(b64data)
        }

        val detail by lazy {
            try {
                binaryData.deserializeStoreAndForward().toDetail()
            } catch (e: Exception) {
                XmlTool.logger.error("Unable to deserialize quarantine data", e)
                val serializedData = SerializationDumper(binaryData).parseStream().lines()
                Detail(
                    title = "Error",
                    message = "Failed to deserialize: ${e.message}",
                    body = serializedData,
                )
            }
        }
    }

    companion object {
        operator fun invoke(file: List<String>): QuarantineViewer? {
            val document = XML_FACTORY.parse(file.joinToString("\n").byteInputStream())
            val cacheEntries = document.getElementsByTagName("base64")

            val data = (0..<cacheEntries.length)
                .map { i ->
                    QuarantineRow(cacheEntries.item(i).textContent)
                }

            return if (data.isNotEmpty()) {
                QuarantineViewer(data)
            } else {
                null
            }
        }
    }
}
