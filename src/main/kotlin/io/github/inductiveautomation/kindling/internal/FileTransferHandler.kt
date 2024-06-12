package io.github.inductiveautomation.kindling.internal

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import javax.swing.TransferHandler

class FileTransferHandler(
    private val predicate: (File) -> Boolean = { true },
    private val callback: (List<File>) -> Unit,
) : TransferHandler() {
    override fun canImport(support: TransferSupport): Boolean {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }

    @Suppress("UNCHECKED_CAST")
    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) {
            return false
        }
        val t = support.transferable
        try {
            val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            files.filter(predicate)
                .takeIf { it.isNotEmpty() }
                ?.let(callback)
        } catch (e: UnsupportedFlavorException) {
            return false
        } catch (e: IOException) {
            return false
        }
        return true
    }
}
