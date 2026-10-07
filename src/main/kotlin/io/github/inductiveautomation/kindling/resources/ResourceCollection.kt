package io.github.inductiveautomation.kindling.resources

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.net.URLDecoder
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A single collection of resources on disk - a deployment mode (under `config/resources`) or a project (under
 * `projects`). Holds only the resources physically defined in this collection; inheritance via [CollectionManifest.parent]
 * is not applied.
 */
class ResourceCollection(
    val name: String,
    val directory: Path,
    val manifest: CollectionManifest,
) {
    val resources: Map<ResourcePath, Resource> by lazy { loadResources() }

    operator fun get(path: ResourcePath): Resource? = resources[path]

    /**
     * Retrieve the singleton resource of the given [type], if defined.
     */
    operator fun get(type: ResourceType): Resource? = resources[ResourcePath(type)]

    fun resourcesOfType(type: ResourceType): List<Resource> = resources.values.filter { it.type == type }

    override fun toString(): String = "ResourceCollection($name)"

    private fun loadResources(): Map<ResourcePath, Resource> = buildMap {
        for (moduleDir in directory.childDirectories()) {
            for (typeDir in moduleDir.childDirectories()) {
                visit(ResourceType(moduleDir.name, typeDir.name), typeDir, emptyList())
            }
        }
    }

    // Mirrors ResourceCollectionFileTree: a directory with a resource.json is a resource, and nothing beneath it is
    // considered; a directory with a unary-resource.json defines a '_unary' resource but is still descended into.
    private fun MutableMap<ResourcePath, Resource>.visit(type: ResourceType, dir: Path, segments: List<String>) {
        val manifestFile = dir / RESOURCE_MANIFEST
        if (manifestFile.exists()) {
            val path = ResourcePath(type, segments)
            put(path, Resource(name, path, readManifest(manifestFile), dir))
            return
        }

        val unaryManifestFile = dir / UNARY_RESOURCE_MANIFEST
        val hasUnary = unaryManifestFile.exists()
        if (hasUnary) {
            val path = ResourcePath(type, segments + UNARY_RESOURCE_NAME)
            put(path, Resource(name, path, readManifest(unaryManifestFile), dir))
        }

        for (child in dir.childDirectories()) {
            val segment = unescape(child.name)
            if (hasUnary && segment == UNARY_RESOURCE_NAME) continue
            visit(type, child, segments + segment)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readManifest(file: Path): ResourceManifest = file.inputStream().use(ResourceJson::decodeFromStream)

    companion object {
        const val CONFIG_MODE_MANIFEST = "config-mode.json"
        const val PROJECT_MANIFEST = "project.json"
        const val RESOURCE_MANIFEST = "resource.json"
        const val UNARY_RESOURCE_MANIFEST = "unary-resource.json"
        const val UNARY_RESOURCE_NAME = "_unary"

        // reserved deployment mode names
        const val SYSTEM = "system"
        const val EXTERNAL = "external"
        const val CORE = "core"
        const val LOCAL = "local"

        /**
         * Load deployment modes from a `config/resources` directory, keyed case-insensitively by name.
         */
        fun ofConfig(root: Path): Map<String, ResourceCollection> = loadAll(root, CONFIG_MODE_MANIFEST)

        /**
         * Load projects from a `projects` directory, keyed case-insensitively by name.
         */
        fun ofProjects(root: Path): Map<String, ResourceCollection> = loadAll(root, PROJECT_MANIFEST)

        @OptIn(ExperimentalSerializationApi::class)
        private fun loadAll(root: Path, manifestFileName: String): Map<String, ResourceCollection> {
            val collections = TreeMap<String, ResourceCollection>(String.CASE_INSENSITIVE_ORDER)
            for (dir in root.childDirectories()) {
                val manifestFile = dir / manifestFileName
                if (!manifestFile.exists()) continue
                val manifest: CollectionManifest = manifestFile.inputStream().use(ResourceJson::decodeFromStream)
                collections[dir.name] = ResourceCollection(dir.name, dir, manifest)
            }
            return collections
        }
    }
}

// Ignition only escapes names below the resource type directory, and only decodes segments containing '%'
private fun unescape(segment: String): String = if ('%' in segment) {
    URLDecoder.decode(segment, Charsets.UTF_8)
} else {
    segment
}

private fun Path.childDirectories(): List<Path> = listDirectoryEntries().filter { child ->
    val name = child.name
    child.isDirectory() && !name.startsWith(".") && ".deleted-" !in name && !name.endsWith(".tmp")
}
