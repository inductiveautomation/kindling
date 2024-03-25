package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane.TabType
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Calculator.PERSPECTIVE_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Calculator.VISION_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Calculator.hasPerspectiveResources
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Calculator.hasVisionResources
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Project
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Resource
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.ResourceType
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.asActionIcon
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.SortOrder

class ProjectStatisticsRenderer : StatisticRenderer<ProjectStatistics> {
    override val title: String = "Projects"
    override val icon: Icon = FlatSVGIcon("icons/bx-folder-open.svg").asActionIcon()

    override fun ProjectStatistics.subtitle(): String {
        return sequence {
            if (perspectiveProjects > 0) {
                this.yield("$perspectiveProjects Perspective")
            }
            if (visionProjects > 0) {
                this.yield("$visionProjects Vision")
            }
            this.yield("${projects.size} total")
        }.joinToString()
    }

    override fun ProjectStatistics.render(): JComponent {
        return FlatTabbedPane().apply {
            tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
            tabType = TabType.underlined

            for (project in projects.sortedByDescending { it.resources.size }) {
                val icon = if (project.hasVisionResources || project.hasPerspectiveResources) {
                    VISUALIZATION_ICON
                } else {
                    null
                }

                val tooltip = buildString {
                    append(project.title ?: project.name)
                    if (project.description != null) {
                        append(": ")
                        append(project.description)
                    }
                    if (project.hasVisionResources) {
                        appendLine()
                        append("Contains Vision Resources")
                    }
                    if (project.hasPerspectiveResources) {
                        appendLine()
                        append("Contains Perspective Resources")
                    }
                }
                addTab(project.name, icon, FlatScrollPane(createProjectTable(project)), tooltip)
            }
        }
    }

    private fun createProjectTable(project: Project): JComponent {
        val data = project.resources
            .groupingBy(Resource::type)
            .eachCount()
            .entries
            .toList()
        val model = ReifiedListTableModel(data, ProjectResourceColumns)
        return ReifiedJXTable(model).apply {
            setSortOrder(Count, SortOrder.DESCENDING)
        }
    }

    companion object ProjectResourceColumns : ColumnList<Map.Entry<ResourceType, Int>>() {
        val Type by column { (key, _) ->
            COMMON_RESOURCE_TYPES[key] ?: "${key.moduleId}.${key.typeId}"
        }
        val Count by column { it.value }

        private val VISUALIZATION_ICON = FlatSVGIcon("icons/bx-show.svg").asActionIcon()

        private const val PLATFORM_ID = "ignition"

        private val COMMON_RESOURCE_TYPES = mapOf(
            ResourceType("com.inductiveautomation.alarm-notification", "alarm-pipelines") to "Alarm Pipelines",
            ResourceType("com.inductiveautomation.reporting", "reports") to "Reports",
            ResourceType("com.inductiveautomation.sqlbridge", "transaction-groups") to "Transaction Groups",
            ResourceType("com.inductiveautomation.webdev", "resources") to "Webdev Resources",
            ResourceType(PERSPECTIVE_MODULE_ID, "general-properties") to "Perspective Project Properties",
            ResourceType(PERSPECTIVE_MODULE_ID, "inactivity-properties") to "Perspective Inactivity Properties",
            ResourceType(PERSPECTIVE_MODULE_ID, "page-config") to "Perspective Page Config",
            ResourceType(PERSPECTIVE_MODULE_ID, "session-permissions") to "Perspective Session Permissions",
            ResourceType(PERSPECTIVE_MODULE_ID, "session-props") to "Perspective Session Properties",
            ResourceType(PERSPECTIVE_MODULE_ID, "session-scripts") to "Session Event Scripts",
            ResourceType(PERSPECTIVE_MODULE_ID, "style-classes") to "Perspective Style Classes",
            ResourceType(PERSPECTIVE_MODULE_ID, "stylesheet") to "Perspective Advanced Stylesheet",
            ResourceType(PERSPECTIVE_MODULE_ID, "views") to "Perspective Views",
            ResourceType(PLATFORM_ID, "designer-properties") to "Designer Properties",
            ResourceType(PLATFORM_ID, "event-scripts") to "Gateway Event Scripts",
            ResourceType(PLATFORM_ID, "global-props") to "Project Properties",
            ResourceType(PLATFORM_ID, "named-query") to "Named Queries",
            ResourceType(PLATFORM_ID, "script-python") to "Project Library Scripts",
            ResourceType(VISION_MODULE_ID, "client-event-scripts") to "Client Event Scripts",
            ResourceType(VISION_MODULE_ID, "client-tags") to "Vision Client Tags",
            ResourceType(VISION_MODULE_ID, "designer-properties") to "Vision Designer Properties",
            ResourceType(VISION_MODULE_ID, "general-properties") to "Vision Project Properties",
            ResourceType(VISION_MODULE_ID, "launch-properties") to "Vision Launch Properties",
            ResourceType(VISION_MODULE_ID, "login-properties") to "Vision Login Properties",
            ResourceType(VISION_MODULE_ID, "polling-properties") to "Vision Polling Properties",
            ResourceType(VISION_MODULE_ID, "templates") to "Vision Templates",
            ResourceType(VISION_MODULE_ID, "ui-properties") to "Vision UI Properties",
            ResourceType(VISION_MODULE_ID, "windows") to "Vision Windows",
        )
    }
}
