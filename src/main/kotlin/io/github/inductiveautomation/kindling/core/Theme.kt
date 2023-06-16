package io.github.inductiveautomation.kindling.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jfree.chart.JFreeChart
import javax.swing.UIManager
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme

data class Theme(
    val name: String,
    val isDark: Boolean,
    val lookAndFeelClassname: String,
    val rSyntaxThemeName: String,
) {
    private val rSyntaxTheme: RSyntaxTheme by lazy {
        RSyntaxTheme::class.java.getResourceAsStream("themes/$rSyntaxThemeName").use(RSyntaxTheme::load)
    }

    fun apply(textArea: RSyntaxTextArea) {
        rSyntaxTheme.apply(textArea)
    }

    fun apply(chart: JFreeChart) {
        chart.xyPlot.apply {
            backgroundPaint = UIManager.getColor("Panel.background")
            domainAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
            rangeAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
        }
        chart.backgroundPaint = UIManager.getColor("Panel.background")
    }

    companion object ThemeSerializer : KSerializer<Theme> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kindling.Theme", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Theme) = encoder.encodeString(value.name)
        override fun deserialize(decoder: Decoder): Theme = Kindling.themes.getValue(decoder.decodeString())
    }
}
