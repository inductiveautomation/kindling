package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.SwingConstants.CENTER
import kotlin.io.path.extension

class ImageView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    init {
        val imageInputStream = ImageIO.createImageInputStream(provider.newInputStream(path))
        val image = imageInputStream.use { iis ->
            val reader = ImageIO.getImageReaders(iis).next()
            reader.input = iis
            reader.read(0)
        }

        add(
            JLabel().apply {
                horizontalAlignment = CENTER
                verticalAlignment = CENTER
                icon = ImageIcon(image)
            },
            "center",
        )
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-image.svg").derive(16, 16)

    companion object {
        private val KNOWN_EXTENSIONS = setOf(
            "png",
            "bmp",
            "gif",
            "jpg",
            "jpeg",
        )

        fun isImageFile(path: Path) = path.extension in KNOWN_EXTENSIONS
    }
}
