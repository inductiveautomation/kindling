package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.SimpleScrollPane
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.utils.asActionIcon
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.SwingConstants.CENTER
import kotlin.io.path.extension

class ImageView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    init {
        val image = try {
            ImageIO.createImageInputStream(provider.newInputStream(path)).use { iis ->
                val reader = ImageIO.getImageReaders(iis).next()
                reader.input = iis
                reader.read(0)
            }
        } catch (e: Exception) {
            throw ToolOpeningException("Unable to open ${path.fileName} as an image", e)
        }

        add(
            SimpleScrollPane(
                JLabel().apply {
                    horizontalAlignment = CENTER
                    verticalAlignment = CENTER
                    icon = ImageIcon(image)
                },
            ),
            "center",
        )
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-image.svg").asActionIcon()

    companion object {
        private val KNOWN_EXTENSIONS = setOf(
            "png",
            "bmp",
            "gif",
            "jpg",
            "jpeg",
        )

        fun isImageFile(path: Path) = path.extension.lowercase() in KNOWN_EXTENSIONS
    }
}
