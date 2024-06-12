package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.walk

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
        val resources: List<Resource> = emptyList(),
    )

    data class Resource(
        val type: ResourceType,
        val name: String?,
        val path: String?,
    ) {
        constructor(
            moduleId: String,
            typeId: String,
            name: String? = null,
            path: String? = null,
        ) : this(ResourceType(moduleId, typeId), name, path)
    }

    data class ResourceType(
        val moduleId: String,
        val typeId: String,
    )

    companion object Calculator : StatisticCalculator<ProjectStatistics> {
        const val PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective"
        const val VISION_MODULE_ID = "com.inductiveautomation.vision"

        val Project.hasVisionResources
            get() = resources.any { it.type.moduleId == VISION_MODULE_ID }

        val Project.hasPerspectiveResources
            get() = resources.any { it.type.moduleId == PERSPECTIVE_MODULE_ID }

        @OptIn(ExperimentalSerializationApi::class)
        private val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        @Serializable
        private data class ProjectJson(
            val title: String?,
            val description: String?,
            val parent: String?,
            val enabled: Boolean,
            val inheritable: Boolean,
        )

        @OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
        override suspend fun calculate(backup: GatewayBackup): ProjectStatistics? {
            val projects = backup.projectsDirectory
            if (projects.notExists()) {
                // Either there's no projects (unlikely) or they're encoded in the IDB, which we're not dealing with
                return null
            }

            return ProjectStatistics(
                projects.listDirectoryEntries().mapNotNull { projectPath ->
                    if (!projectPath.isDirectory()) {
                        return@mapNotNull null
                    }
                    val manifest = projectPath.resolve("project.json").inputStream().use { JSON.decodeFromStream<ProjectJson>(it) }
                    val resources =
                        buildList {
                            projectPath.forEachDirectoryEntry { moduleId ->
                                if (moduleId.isDirectory()) {
                                    moduleId.forEachDirectoryEntry { typeId ->
                                        if (typeId.isDirectory()) {
                                            if (typeId.resolve("resource.json").exists()) {
                                                // singleton
                                                add(Resource(moduleId.name, typeId.name))
                                            } else {
                                                typeId.walk(PathWalkOption.INCLUDE_DIRECTORIES)
                                                    .filter { it.name == "resource.json" }
                                                    .forEach { path ->
                                                        add(
                                                            Resource(
                                                                moduleId.name,
                                                                typeId.name,
                                                                path.parent.name,
                                                                path.parent.parent.pathString,
                                                            ),
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    Project(
                        projectPath.name,
                        manifest.title.takeUnless(String?::isNullOrEmpty),
                        manifest.description.takeUnless(String?::isNullOrEmpty),
                        manifest.enabled,
                        manifest.parent,
                        manifest.inheritable,
                        resources,
                    )
                },
            )
        }
    }
}
