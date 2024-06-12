package io.github.inductiveautomation.kindling.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI
import javax.swing.event.HyperlinkEvent

@Serializable
@Suppress("ktlint:standard:trailing-comma-on-declaration-site")
enum class LinkHandlingStrategy(val description: String) {
    OpenInBrowser("Open links in default browser") {
        override fun handleEvent(event: HyperlinkEvent) {
            Desktop.getDesktop().browse(event.url.toURI())
        }
    },
    OpenInIde("Open links in IntelliJ (requires Youtrack plugin)") {
        private val scope = CoroutineScope(Dispatchers.IO)

        override fun handleEvent(event: HyperlinkEvent) {
            for (port in 63330..63339) {
                scope.launch {
                    try {
                        URI.create("http://localhost:$port/file?${event.url.query}").toURL().openConnection().getInputStream().use { input ->
                            input.readAllBytes()
                        }
                    } catch (e: Exception) {
                        // ignored - the Youtrack plugin listens on any of the 10 ports it can,
                        // so we have to blindly broadcast to them all
                    }
                }
            }
        }
    };

    abstract fun handleEvent(event: HyperlinkEvent)
}
