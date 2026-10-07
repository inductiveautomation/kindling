package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.resources.ResourcePath
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Filesystem
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.InternalDatabase
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator

data class ProjectStatistics(
    val projects: List<Project>,
) : Statistic {
    val perspectiveProjects = projects.count { it.hasPerspectiveResources }
    val visionProjects = projects.count { it.hasVisionResources }

    data class Project(
        val name: String,
        val title: String?,
        val description: String?,
        val enabled: Boolean,
        val parent: String?,
        val inheritable: Boolean,
        val resources: List<ResourcePath> = emptyList(),
    )

    companion object Calculator : StatisticCalculator<ProjectStatistics> {
        const val PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective"
        const val VISION_MODULE_ID = "com.inductiveautomation.vision"

        val Project.hasVisionResources
            get() = resources.any { it.type.moduleId == VISION_MODULE_ID }

        val Project.hasPerspectiveResources
            get() = resources.any { it.type.moduleId == PERSPECTIVE_MODULE_ID }

        // 8.0+ projects are stored in the same format regardless of how the rest of the gateway config is stored
        override suspend fun calculate(backup: InternalDatabase): ProjectStatistics? = calculateProjects(backup)

        override suspend fun calculate(backup: Filesystem): ProjectStatistics? = calculateProjects(backup)

        private fun calculateProjects(backup: GatewayBackup): ProjectStatistics? {
            // Either there's no projects (unlikely) or they're encoded in the IDB (7.9), which we're not dealing with
            val projects = backup.projects ?: return null

            return ProjectStatistics(
                projects.values.map { collection ->
                    val manifest = collection.manifest
                    Project(
                        name = collection.name,
                        title = manifest.title.takeUnless(String?::isNullOrEmpty),
                        description = manifest.description.takeUnless(String?::isNullOrEmpty),
                        enabled = manifest.enabled,
                        parent = manifest.parent,
                        inheritable = manifest.inheritable,
                        resources = collection.resources.keys.toList(),
                    )
                },
            )
        }
    }
}
