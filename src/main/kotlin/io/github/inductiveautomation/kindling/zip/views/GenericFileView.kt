package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import java.awt.EventQueue
import java.awt.Font
import java.awt.Rectangle
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.HexFormat
import javax.swing.Icon
import javax.swing.JTextArea

class GenericFileView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    override val icon: Icon? = null

    private val textArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        provider.newInputStream(path).use { file ->
            val windowSize = 16
            textArea.text = sequence {
                val buffer = ByteArray(windowSize)
                var numberOfBytesRead: Int
                do {
                    numberOfBytesRead = file.readNBytes(buffer, 0, windowSize)

                    // the last read might not be complete, so there could be stale data in the buffer
                    val toRead = buffer.sliceArray(0 until numberOfBytesRead)
                    val hexBytes = HEX_FORMAT.formatHex(toRead)
                    val decodedBytes = decodeBytes(toRead)
                    yield("${hexBytes.padEnd(47)}  $decodedBytes")
                } while (numberOfBytesRead == windowSize)
            }.joinToString(separator = "\n")
        }

        add(FlatScrollPane(textArea), "push, grow")
        EventQueue.invokeLater {
            textArea.scrollRectToVisible(Rectangle(0, 0))
        }
    }

    private fun decodeBytes(toRead: ByteArray): String {
        return String(
            CharArray(toRead.size) { i ->
                val byte = toRead[i]
                if (byte >= 0 && !Character.isISOControl(byte.toInt())) {
                    Char(byte.toUShort())
                } else {
                    '.'
                }
            },
        )
    }

    companion object {
        private val HEX_FORMAT: HexFormat = HexFormat.of().withDelimiter(" ")
    }
}
