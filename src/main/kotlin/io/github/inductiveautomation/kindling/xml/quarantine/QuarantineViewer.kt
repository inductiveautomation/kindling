package io.github.inductiveautomation.kindling.xml.quarantine

import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.utils.XML_FACTORY
import io.github.inductiveautomation.kindling.utils.deserializeStoreAndForward
import io.github.inductiveautomation.kindling.utils.parse
import io.github.inductiveautomation.kindling.utils.toDetail
import io.github.inductiveautomation.kindling.xml.XmlTool
import net.miginfocom.swing.MigLayout
import java.awt.EventQueue
import java.awt.Rectangle
import javax.swing.JPanel
import com.inductiveautomation.ignition.common.Base64 as IaBase64

class QuarantineViewer(data: List<Detail>) : JPanel(MigLayout("ins 6, fill, hidemode 3")) {
    init {
        val detailsPane = DetailsPane(data)
        EventQueue.invokeLater {
            detailsPane.scrollRectToVisible(Rectangle(0, 0))
        }
        add(detailsPane, "push, grow")
    }

    companion object {
        operator fun invoke(file: List<String>): QuarantineViewer? {
            val document = XML_FACTORY.parse(file.joinToString("\n").byteInputStream())
            val cacheEntries = document.getElementsByTagName("base64")

            val data = (0..<cacheEntries.length)
                .mapNotNull(cacheEntries::item)
                .map { it.textContent }
                .map { IaBase64.decodeAndGunzip(it) }
                .map { bytes ->
                    try {
                        bytes.deserializeStoreAndForward().toDetail()
                    } catch (e: Exception) {
                        XmlTool.logger.error("Unable to deserialize quarantine data", e)
                        Detail(
                            title = "Error",
                            message = "Unable to deserialize quarantine data",
                            body = listOfNotNull(e.message),
                        )
                    }
                }

            return if (data.isNotEmpty()) {
                QuarantineViewer(data)
            } else {
                null
            }
        }
    }
}
