package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.asActionIcon
import java.io.File
import javax.swing.Icon
import javax.swing.filechooser.FileView

class CustomIconView : FileView() {
    override fun getIcon(file: File): Icon? = if (file.isFile) {
        Tool.find(file)?.icon?.asActionIcon()
    } else {
        null
    }

    override fun getTypeDescription(file: File) = if (file.isFile) {
        Tool.find(file)?.description
    } else {
        null
    }
}
