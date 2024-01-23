package io.github.inductiveautomation.kindling.xml

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.serialization.Serializable
import java.io.File

/*
The very basic structure of the configuration file can be described as, <configuration> element,
containing zero or more <appender> elements, followed by zero or more <logger> elements,
followed by at most one <root> element.
 */
@JacksonXmlRootElement(localName = "configuration")
@JsonPropertyOrder("logHomeDir") // ensure that "logHomeDir" is declared before other elements that reference its value
data class LogbackConfigData(
    @field:JacksonXmlProperty(isAttribute = true, localName = "debug")
    var debug: Boolean = true,
    @field:JacksonXmlProperty(isAttribute = true, localName = "scan")
    var scan: Boolean? = null,
    @field:JacksonXmlProperty(isAttribute = true, localName = "scanPeriod")
    var scanPeriod: String? = null,
    @field:JacksonXmlProperty(localName = "property")
    var logHomeDir: LogHomeDirectory? = null,
    @field:JacksonXmlProperty(localName = "root")
    var root: Root = Root("INFO"),
    @JacksonXmlProperty(localName = "appender")
    @JacksonXmlElementWrapper(useWrapping = false)
    var appender: List<Appender>? = listOf(),
    @JacksonXmlProperty(localName = "logger")
    @JacksonXmlElementWrapper(useWrapping = false)
    var logger: List<Logger>? = listOf(),
)

/*
The log home directory is a <property> element which stores the root log output folder as its value.
 */
@JacksonXmlRootElement
data class LogHomeDirectory(
    @field:JacksonXmlProperty(isAttribute = true, localName = "name")
    var name: String = "LOG_HOME",
    @field:JacksonXmlProperty(isAttribute = true, localName = "value")
    var value: String = System.getProperty("user.home"),
)

/*
The <root> element configures the root logger. It supports a single attribute, namely the level attribute.
It does not allow any other attributes because the additivity flag does not apply to the root logger.
Moreover, since the root logger is already named as "ROOT", it does not allow a name attribute either.
The value of the level attribute can be one of the case-insensitive strings TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF.
Note that the level of the root logger cannot be set to INHERITED or NULL.

Similarly to the <logger> element, the <root> element may contain zero or more <appender-ref> elements;
each appender thus referenced is added to the root logger.
 */
@JacksonXmlRootElement(localName = "root")
data class Root(
    @field:JacksonXmlProperty(isAttribute = true, localName = "level")
    var level: String? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(localName = "appender-ref", useWrapping = false)
    var appenderRef: MutableList<AppenderRef>? =
        mutableListOf(
            AppenderRef("SysoutAsync"),
            AppenderRef("DBAsync"),
        ),
)

/*
An appender is configured with the <appender> element, which takes two mandatory attributes: name and class.
The name attribute specifies the name of the appender whereas the class attribute specifies the fully qualified name of
the appender class to instantiate. The <appender> element may contain zero or one <layout> elements, zero or more
<encoder> elements and zero or more <filter> elements.
Apart from these three common elements, <appender> elements may contain any number of elements corresponding to
JavaBean properties of the appender class.
 */
@JacksonXmlRootElement(localName = "appender")
data class Appender(
    @field:JacksonXmlProperty(isAttribute = true, localName = "name")
    var name: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    var className: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "queueSize")
    var queueSize: String? = null,
    @field:JacksonXmlProperty(isAttribute = true, localName = "discardingThreshold")
    var discardingThreshold: String? = null,
    @field:JacksonXmlProperty(localName = "rollingPolicy")
    var rollingPolicy: RollingPolicy? = null,
    @JacksonXmlProperty(localName = "encoder")
    @JacksonXmlElementWrapper(useWrapping = false)
    var encoder: MutableList<Encoder>? = mutableListOf(),
    @JacksonXmlProperty(localName = "filter")
    @JacksonXmlElementWrapper(useWrapping = false)
    var levelFilter: LevelFilter? = null,
    @field:JacksonXmlProperty(localName = "dir")
    var dir: String? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(useWrapping = false)
    var appenderRef: MutableList<AppenderRef>? = mutableListOf(),
)

data class AppenderRef(
    @field:JacksonXmlProperty(isAttribute = true, localName = "ref")
    var ref: String,
)

data class Encoder(
    @field:JacksonXmlProperty(localName = "pattern")
    var pattern: String = "%.-1p [%-30c{1}] [%d{MM:dd:YYYY HH:mm:ss, America/Los_Angeles}]: %m %X%n",
)

/*
A <logger> element takes exactly one mandatory name attribute, an optional level attribute, and an optional additivity
attribute, admitting the values true or false. The value of the level attribute admitting one of the case-insensitive
string values TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. The special case-insensitive value INHERITED, or its synonym
NULL, will force the level of the logger to be inherited from higher up in the hierarchy. This comes in handy if you
set the level of a logger and later decide that it should inherit its level.
The <logger> element may contain zero or more <appender-ref> elements.
*/
@JacksonXmlRootElement(localName = "logger")
data class Logger(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    var name: String,
    @JacksonXmlProperty(isAttribute = true, localName = "level")
    var level: String? = null,
    @JacksonXmlProperty(isAttribute = true, localName = "additivity")
    var additivity: Boolean? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(useWrapping = false)
    var appenderRef: MutableList<AppenderRef>? = mutableListOf(),
)

/*
The <filter> element filters events based on exact level matching.
If the event's level is equal to the configured level, the filter accepts or denies the event, depending on the
configuration of the onMatch and onMismatch properties.
 */
@JacksonXmlRootElement(localName = "filter")
data class LevelFilter(
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    var className: String = "ch.qos.logback.classic.filter.LevelFilter",
    @field:JacksonXmlProperty(localName = "level")
    var level: String,
    @field:JacksonXmlProperty(localName = "onMatch")
    var onMatch: String? = "ACCEPT",
    @field:JacksonXmlProperty(localName = "onMismatch")
    var onMismatch: String? = "DENY",
)

/*
Sometimes you may wish to archive files essentially by date but at the same time limit the size of each log file,
in particular if post-processing tools impose size limits on the log files.
In order to address this requirement, logback ships with SizeAndTimeBasedRollingPolicy.

Note the "%i" conversion token in addition to "%d". Both the %i and %d tokens are mandatory.
Each time the current log file reaches maxFileSize before the current time period ends,
it will be archived with an increasing index, starting at 0.

Size and time based archiving supports deletion of old archive files.
You need to specify the number of periods to preserve with the maxHistory property.
When your application is stopped and restarted, logging will continue at the correct location,
i.e. at the largest index number for the current period.
 */
@JacksonXmlRootElement(localName = "rollingPolicy")
data class RollingPolicy(
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    var className: String = "ch.qos.logback.core.rolling.RollingFileAppender",
    @field:JacksonXmlProperty(localName = "fileNamePattern")
    var fileNamePattern: String = "\${ROOT}\\\\AdditionalLogs.%d{yyyy-MM-dd}.%i.log",
    @field:JacksonXmlProperty(localName = "maxFileSize")
    var maxFileSize: String = "10MB",
    @field:JacksonXmlProperty(localName = "totalSizeCap")
    var totalSizeCap: String = "1GB",
    @field:JacksonXmlProperty(localName = "maxHistory")
    var maxHistory: String = "5",
)

class LogbackConfigManager(
    var configs: LogbackConfigData,
) {
    // Build XmlMapper with the parameters for serialization
    private val xmlMapperBuilder =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build()

    private val xmlMapper: XmlMapper =
        xmlMapperBuilder.apply {
            setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            enable(SerializationFeature.INDENT_OUTPUT)
        }

    // Convert LogbackConfigData data class to XML string (for UI and clipboard)
    fun getXmlString(): String {
        return xmlMapper.writeValueAsString(configs)
    }

    // Convert LogbackConfigData data class to XML file (serialization)
    fun writeXmlFile(filePathString: String) {
        xmlMapper.writeValue(File(filePathString), configs)
    }

    fun getLoggerConfigs(): MutableList<SelectedLogger> {
        val selectedLoggers = mutableListOf<SelectedLogger>()

        configs.logger?.forEach { logger ->
            configs.appender?.forEach { appender ->
                if (logger.name == appender.name) {
                    // If there is a separate output appender, it is guaranteed to have a rolling policy
                    val rollingPolicy = appender.rollingPolicy!!
                    val pathSplit = rollingPolicy.fileNamePattern.split("\\").filter { it.isNotBlank() }

                    selectedLoggers.add(
                        SelectedLogger(
                            name = logger.name,
                            level = logger.level ?: "INFO",
                            separateOutput = true,
                            outputFolder = pathSplit.minus(pathSplit.last()).joinToString(separator = "\\\\") + "\\\\",
                            filenamePattern = pathSplit.last().toString(),
                            maxFileSize = rollingPolicy.maxFileSize.filter(Char::isDigit).toLong(),
                            totalSizeCap = rollingPolicy.totalSizeCap.filter(Char::isDigit).toLong(),
                            maxDaysHistory = rollingPolicy.maxHistory.filter(Char::isDigit).toLong(),
                        ),
                    )
                }
            }
        }

        return selectedLoggers
    }

    /*
    Each selected logger will either output to a separate appender or use the default Sysout appender.
    In either case, we need a <logger> element.
    For those using a separate appender, we need to generate that <appender> element.
     */
    fun updateLoggerConfigs(selectedLoggers: MutableList<SelectedLogger>) {
        val separateOutputLoggers =
            selectedLoggers.filter { selectedLogger: SelectedLogger ->
                selectedLogger.separateOutput
            }

        val loggerElements =
            selectedLoggers.map { selectedLogger: SelectedLogger ->
                Logger(
                    name = selectedLogger.name,
                    level = selectedLogger.level,
                    additivity = false,
                    appenderRef =
                        if (selectedLogger.separateOutput) {
                            mutableListOf(AppenderRef(selectedLogger.name))
                        } else {
                            mutableListOf(AppenderRef("SysoutAsync"), AppenderRef("DBAsync"))
                        },
                )
            }

        val appenderElements =
            separateOutputLoggers.map { separateOutputLogger: SelectedLogger ->
                Appender(
                    name = separateOutputLogger.name,
                    className = "ch.qos.logback.core.rolling.RollingFileAppender",
                    rollingPolicy =
                        RollingPolicy(
                            className = "ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy",
                            fileNamePattern = separateOutputLogger.outputFolder + separateOutputLogger.filenamePattern,
                            maxFileSize = separateOutputLogger.maxFileSize.toString() + "MB",
                            totalSizeCap = separateOutputLogger.totalSizeCap.toString() + "MB",
                            maxHistory = separateOutputLogger.maxDaysHistory.toString(),
                        ),
                    encoder =
                        mutableListOf(
                            Encoder(
                                pattern =
                                    "%.-1p [%-30logger] [%d{YYYY/MM/dd HH:mm:ss, SSS}]: {%thread} %replace(%m){\"[\\r\\n]+\", \"\"} %X%n",
                            ),
                        ),
                )
            }

        configs.logger = loggerElements
        configs.appender =
            appenderElements.plus(DEFAULT_APPENDERS)
    }

    companion object {
        val DEFAULT_APPENDERS =
            listOf(
                Appender(
                    name = "SysoutAppender",
                    className = "ch.qos.logback.core.ConsoleAppender",
                    encoder =
                        mutableListOf(
                            Encoder(
                                pattern = "%.-1p [%-30c{1}] [%d{HH:mm:ss,SSS}]: %m %X%n",
                            ),
                        ),
                ),
                Appender(
                    name = "DB",
                    className = "com.inductiveautomation.logging.SQLiteAppender",
                    dir = "logs",
                ),
                Appender(
                    name = "SysoutAsync",
                    className = "ch.qos.logback.classic.AsyncAppender",
                    queueSize = "1000",
                    discardingThreshold = "0",
                    appenderRef = mutableListOf(AppenderRef(ref = "SysoutAppender")),
                ),
                Appender(
                    name = "DBAsync",
                    className = "ch.qos.logback.classic.AsyncAppender",
                    queueSize = "100000",
                    discardingThreshold = "0",
                    appenderRef = mutableListOf(AppenderRef(ref = "DB")),
                ),
            )
    }
}

class LogbackConfigDeserializer {
    private val xmlModule =
        JacksonXmlModule().apply {
            setDefaultUseWrapper(false)
        }

    private val xmlMapper =
        XmlMapper(xmlModule).registerKotlinModule().apply {
            jacksonMapperBuilder().apply {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                enable(MapperFeature.USE_ANNOTATIONS)
            }
        }

    // Read in XML file as LogbackConfigData data class (deserialization)
    fun getObjectFromXML(filePath: String): LogbackConfigData? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                xmlMapper.readValue(file, LogbackConfigData::class.java)
            } else {
                println("File not found: $filePath")
                null
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            null
        }
    }
}

@Serializable
data class IgnitionLogger(val name: String)
