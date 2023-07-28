package io.github.inductiveautomation.kindling.core

import java.io.File
import javax.swing.Icon
import javax.swing.filechooser.FileView

class CustomIconView : FileView() {
    override fun getIcon(file: File): Icon? = if (file.isFile) {
        Tool.find(file)?.icon?.derive(16, 16)
    } else {
        null
    }

    override fun getTypeDescription(file: File) = if (file.isFile) {
        Tool.find(file)?.description
    } else {
        null
    }
}
