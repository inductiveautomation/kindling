package io.github.inductiveautomation.kindling.cache

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import java.nio.file.Path
import kotlin.io.path.extension
import io.github.inductiveautomation.kindling.cache.hsql.CacheView as HsqlCacheView
import io.github.inductiveautomation.kindling.cache.sqlite.CacheView as IdbCacheView

data object CacheViewer : Tool {
    override val serialKey = "sf-cache"
    override val title = "Store & Forward Cache"
    override val description = "S&F Cache (.zip, 8.1: .data, .script, 8.3: .idb)"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    internal val extensions = arrayOf("data", "script", "zip", "idb")
    override val filter = FileFilter(description, *extensions)

    override fun open(path: Path): ToolPanel = when (path.extension) {
        "data",
        "script",
        -> {
            HsqlCacheView(path)
        }
        "idb" -> {
            IdbCacheView.fromIdb(path)
        }
        "zip" -> {
            try {
                HsqlCacheView(path)
            } catch (_: Exception) {
                IdbCacheView.fromZip(path)
            }
        }
        else -> throw ToolOpeningException("Invalid file extension; ${path.extension}")
    }
}
