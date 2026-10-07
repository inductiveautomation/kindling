package io.github.inductiveautomation.kindling.resources

import com.inductiveautomation.ignition.common.datasource.DatabaseVendor
import io.github.inductiveautomation.kindling.resources.ResourceType.Companion.PLATFORM_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.categories.DatabaseStatistics
import io.github.inductiveautomation.kindling.statistics.categories.DeviceStatistics
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics
import io.github.inductiveautomation.kindling.statistics.categories.MetaStatistics
import io.github.inductiveautomation.kindling.statistics.categories.OpcServerStatistics
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics.Calculator.hasPerspectiveResources
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.toPath
import kotlin.io.path.walk

class ResourceCollectionTests :
    FunSpec({
        val root = checkNotNull(ResourceCollectionTests::class.java.getResource("gwbk83")).toURI().toPath()
        val config = ResourceCollection.ofConfig(root / "config/resources")
        val core = config.getValue(ResourceCollection.CORE)

        fun platform(typeId: String) = ResourceType(PLATFORM_MODULE_ID, typeId)

        context("Collections") {
            test("Only directories with a manifest are collections") {
                config.keys shouldBe setOf("core", "local")
            }

            test("Collection names are case insensitive") {
                config["CORE"].shouldNotBeNull().name shouldBe "core"
            }

            test("Manifest") {
                core.manifest shouldBe CollectionManifest(title = "Core", enabled = true, inheritable = true, parent = "external")
                config.getValue("local").manifest.parent shouldBe "core"
            }
        }

        context("Resources") {
            test("Singleton") {
                val resource = core[platform("system-properties")].shouldNotBeNull()
                resource.path.isSingleton shouldBe true
                resource.name.shouldBeNull()
                resource.config["systemName"].toString() shouldBe "\"Fixture Gateway\""
            }

            test("Named resources, with escaped names") {
                core.resourcesOfType(platform("database-connection")).map { it.name } shouldContainExactlyInAnyOrder listOf("MyDB", "Hist:DB")
            }

            test("Nested resources keep their folder path") {
                core.resources shouldContainKey ResourcePath(platform("tag-group"), listOf("default", "Default"))
            }

            test("Unary resources") {
                val type = platform("tag-definition")
                core.resourcesOfType(type).map { it.path } shouldContainExactlyInAnyOrder listOf(
                    ResourcePath(type, listOf("default", "_unary")),
                    ResourcePath(type, listOf("default", "Folder", "_unary")),
                )
            }

            test("Directories beneath a resource are ignored") {
                val type = ResourceType("com.inductiveautomation.opcua", "device")
                core.resources shouldNotContainKey ResourcePath(type, listOf("Sim", "nested"))
                core.resourcesOfType(type).size shouldBe 1
            }

            test("Deleted resources are ignored") {
                core.resourcesOfType(platform("opc-connection")).map { it.name } shouldBe listOf("Ignition OPC UA Server")
            }

            test("Manifest defaults and attributes") {
                val connections = core.resourcesOfType(platform("database-connection")).associateBy { it.name }
                connections.getValue("MyDB").run {
                    description shouldBe "Primary database"
                    enabled shouldBe true
                    manifest.overridable shouldBe true
                    manifest.files shouldBe setOf("config.json")
                }
                connections.getValue("Hist:DB").enabled shouldBe false
            }
        }

        context("Projects") {
            val projects = ResourceCollection.ofProjects(root / "projects")

            test("Project resources") {
                val project = projects.getValue("test")
                project.manifest.title shouldBe "Test Project"
                project.resources.keys.map(ResourcePath::toString) shouldContainExactlyInAnyOrder listOf(
                    "ignition/global-props",
                    "ignition/script-python/lib/util",
                    "com.inductiveautomation.perspective/views/Main/Page",
                )
            }

            test("Project resources use documentation as description") {
                val script = projects.getValue("test")[ResourcePath(platform("script-python"), listOf("lib", "util"))]
                script.shouldNotBeNull().description shouldBe "Utility scripts"
            }

            test("Manifest defaults") {
                projects.getValue("global").manifest shouldBe CollectionManifest(title = "", enabled = false, inheritable = true)
            }
        }

        context("Modern gateway backup statistics") {
            val backup = GatewayBackup.Filesystem(root)

            test("Meta") {
                MetaStatistics.calculate(backup) shouldBe MetaStatistics(
                    uuid = "11111111-2222-3333-4444-555555555555",
                    gatewayName = "Fixture Gateway",
                    edition = "Standard",
                    role = "Independent",
                    version = "8.3.1",
                    initMemory = 1024,
                    maxMemory = 4096,
                )
            }

            test("Databases") {
                val stats = DatabaseStatistics.calculate(backup).shouldNotBeNull()
                stats.connections shouldContainExactlyInAnyOrder listOf(
                    DatabaseStatistics.Connection(
                        name = "MyDB",
                        // the local override is not applied
                        description = "Primary database",
                        vendor = DatabaseVendor.MYSQL,
                        enabled = true,
                        sfEnabled = true,
                        bufferSize = 5000,
                        cacheSize = 25000,
                    ),
                    DatabaseStatistics.Connection(
                        name = "Hist:DB",
                        description = null,
                        vendor = DatabaseVendor.GENERIC,
                        enabled = false,
                        sfEnabled = false,
                        bufferSize = 10_000,
                        cacheSize = 0,
                    ),
                )
                stats.enabled shouldBe 1
            }

            test("Devices") {
                DeviceStatistics.calculate(backup) shouldBe DeviceStatistics(
                    listOf(DeviceStatistics.Device("Sim", "ProgrammableSimulatorDevice", null, true)),
                )
            }

            test("OPC connections") {
                val stats = OpcServerStatistics.calculate(backup).shouldNotBeNull()
                stats.servers shouldBe listOf(
                    OpcServerStatistics.OpcServer(
                        name = "Ignition OPC UA Server",
                        type = OpcServerStatistics.UA_SERVER_TYPE,
                        description = null,
                        readOnly = false,
                        enabled = true,
                    ),
                )
                stats.uaServers shouldBe 1
            }

            test("Gateway network") {
                GatewayNetworkStatistics.calculate(backup) shouldBe GatewayNetworkStatistics(
                    outgoing = listOf(GatewayNetworkStatistics.OutgoingConnection("remote.example.com", 8060, false)),
                    incoming = listOf(GatewayNetworkStatistics.IncomingConnection("remote-gateway")),
                )
            }

            test("Projects") {
                val stats = ProjectStatistics.calculate(backup).shouldNotBeNull()
                stats.projects.map { it.name } shouldContainExactlyInAnyOrder listOf("test", "global")
                val test = stats.projects.single { it.name == "test" }
                test.description.shouldBeNull()
                test.hasPerspectiveResources shouldBe true
                stats.perspectiveProjects shouldBe 1
            }
        }

        context("Backup format detection") {
            fun zipOf(vararg entries: String, emptyFiles: List<String> = emptyList()): Path {
                val zip = createTempFile("kindling", ".gwbk").also { it.deleteIfExists() }
                FileSystems.newFileSystem(URI.create("jar:${zip.toUri()}"), mapOf("create" to "true")).use { fs ->
                    for (entry in entries) {
                        root.walk().filter { it.isRegularFile() && root.relativize(it).pathString.startsWith(entry) }.forEach { file ->
                            val target = fs.getPath(root.relativize(file).pathString)
                            target.parent?.createDirectories()
                            file.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                    for (name in emptyFiles) {
                        fs.getPath(name).createFile()
                    }
                }
                return zip
            }

            test("A config directory means an 8.3 backup") {
                GatewayBackup(zipOf("backupinfo.xml", "config/")).shouldBeInstanceOf<GatewayBackup.Filesystem>()
            }

            test("No config directory means a legacy backup") {
                GatewayBackup(zipOf("backupinfo.xml", emptyFiles = listOf("db_backup_sqlite.idb"))).shouldBeInstanceOf<GatewayBackup.InternalDatabase>()
            }
        }
    })
