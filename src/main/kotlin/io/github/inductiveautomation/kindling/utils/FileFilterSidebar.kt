package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.FilterPanel
import java.nio.file.Path
import java.util.EventListener


/*
 * A Filter Sidebar which automatically manages its filters by setting the models appropriately.
 * The FilterPanel must implement setModelData() in order for it to be responsive to file changes.
 */
class FileFilterSidebar<T>(
    initialPanels: List<FilterPanel<T>?>,
    fileData: Map<Path, Collection<T>>
) : FilterSidebar<T>(initialPanels.filterNotNull()) {
    constructor(
        vararg panels: FilterPanel<T>?,
        fileData: Map<Path, Collection<T>>,
    ) : this(panels.toList(), fileData)

    init {
        require(fileData.isNotEmpty()) { "File data must not be empty. Use FilterSidebar instead." }
    }

    var listModelsAreAdjusting = false
        private set

    private val filePanel = FileFilterPanel(fileData)

    fun isSelectedFileIndex(index: Int) = filePanel.fileList.checkBoxListSelectionModel.isSelectedIndex(index)

    fun addFileFilterChangeListener(l: FileFilterChangeListener) {
        listenerList.add(l)
    }

    init {
        val initialData = filePanel.fileList.model.data.values.flatten()
        for (panel in this) {
            if (panel is FileFilterResponsive<*>) {
                @Suppress("unchecked_cast")
                panel as FileFilterResponsive<T>
                panel.setModelData(initialData)
            }
            panel.reset()
        }

        if (fileData.isNotEmpty()) {
            addTab(
                null,
                filePanel.icon,
                filePanel.component,
                filePanel.formattedTabName,
            )
            filePanel.fileList.selectAll()

            filePanel.addFilterChangeListener {
                listModelsAreAdjusting = true
                val selectedData = filePanel.fileList.checkBoxListSelectedIndices.filter { it != 0 }.flatMap {
                    val entry = filePanel.fileList.model.entries[it - 1]
                    entry.second
                }
                if (selectedData.isNotEmpty()) {
                    for (panel in this) {
                        if (panel is FileFilterResponsive<*>) {
                            @Suppress("unchecked_cast")
                            (panel as FileFilterResponsive<T>).setModelData(selectedData)
                        }
                    }
                }
                listModelsAreAdjusting = false

                // Fire the "external" listeners.
                listenerList.getAll<FileFilterChangeListener>().forEach(FileFilterChangeListener::fileFilterChanged)
            }
        }
    }
}

// This needs to fire after all the list models have been updated or else we run into major problems.
fun interface FileFilterChangeListener : EventListener {
    fun fileFilterChanged()
}