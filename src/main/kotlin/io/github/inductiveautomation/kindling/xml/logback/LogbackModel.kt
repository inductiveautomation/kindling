package io.github.inductiveautomation.kindling.xml.logback

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
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import java.io.OutputStream

/**
 * The very basic structure of the configuration file can be described as, <configuration> element,
 * containing zero or more <appender> elements, followed by zero or more <logger> elements,
 * followed by at most one <root> element.
 */
@JacksonXmlRootElement(localName = "configuration")
@JsonPropertyOrder("logHomeDir") // ensure that "logHomeDir" is declared before other elements that reference its value
data class LogbackConfigData(
    @field:JacksonXmlProperty(isAttribute = true, localName = "debug")
    val debug: Boolean = true,
    @field:JacksonXmlProperty(isAttribute = true, localName = "scan")
    val scan: Boolean? = null,
    @field:JacksonXmlProperty(isAttribute = true, localName = "scanPeriod")
    val scanPeriod: String? = null,
    @field:JacksonXmlProperty(localName = "property")
    val logHomeDir: LogHomeDirectory? = null,
    @field:JacksonXmlProperty(localName = "root")
    val root: Root = Root("INFO"),
    @JacksonXmlProperty(localName = "appender")
    @JacksonXmlElementWrapper(useWrapping = false)
    val appender: List<Appender>? = emptyList(),
    @JacksonXmlProperty(localName = "logger")
    @JacksonXmlElementWrapper(useWrapping = false)
    val logger: List<Logger>? = emptyList(),
) {
    companion object Serializer {
        private val deserializationMapper = XmlMapper(
            JacksonXmlModule().apply {
                setDefaultUseWrapper(false)
            },
        ).registerKotlinModule().apply {
            jacksonMapperBuilder().apply {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                enable(MapperFeature.USE_ANNOTATIONS)
            }
        }

        fun fromXml(file: List<String>): LogbackConfigData {
            return try {
                deserializationMapper.readValue(file.joinToString("\n"), LogbackConfigData::class.java)
            } catch (e: Exception) {
                throw ToolOpeningException("An error occurred: ${e.message}")
            }
        }

        private val serializationMapper: XmlMapper = XmlMapper.builder()
            .defaultUseWrapper(false)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .serializationInclusion(JsonInclude.Include.NON_EMPTY)
            .build()

        val DEFAULT_APPENDERS =
            listOf(
                Appender(
                    name = "SysoutAppender",
                    className = "ch.qos.logback.core.ConsoleAppender",
                    encoder = listOf(
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
                    appenderRef = listOf(AppenderRef(ref = "SysoutAppender")),
                ),
                Appender(
                    name = "DBAsync",
                    className = "ch.qos.logback.classic.AsyncAppender",
                    queueSize = "100000",
                    discardingThreshold = "0",
                    appenderRef = listOf(AppenderRef(ref = "DB")),
                ),
            )
    }

    fun toXml(): String {
        return serializationMapper.writeValueAsString(this)
    }

    /**
     * Write the configuration to the provided output stream, closing the stream.
     */
    fun writeTo(stream: OutputStream) {
        stream.use {
            serializationMapper.writeValue(it, this)
        }
    }
}

/**
 * The log home directory is a <property> element which stores the root log output folder as its value.
 */
@JacksonXmlRootElement
data class LogHomeDirectory(
    @field:JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String = "LOG_HOME",
    @field:JacksonXmlProperty(isAttribute = true, localName = "value")
    val value: String = System.getProperty("user.home"),
)

/**
 * The <root> element configures the root logger. It supports a single attribute, namely the level attribute.
 * It does not allow any other attributes because the additivity flag does not apply to the root logger.
 * Moreover, since the root logger is already named as "ROOT", it does not allow a name attribute either.
 * The value of the level attribute can be one of the case-insensitive strings TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF.
 * Note that the level of the root logger cannot be set to INHERITED or NULL.
 *
 * Similarly to the <logger> element, the <root> element may contain zero or more <appender-ref> elements;
 * each appender thus referenced is added to the root logger.
 */
@JacksonXmlRootElement(localName = "root")
data class Root(
    @field:JacksonXmlProperty(isAttribute = true, localName = "level")
    val level: String? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(localName = "appender-ref", useWrapping = false)
    val appenderRef: List<AppenderRef>? = listOf(
        AppenderRef("SysoutAsync"),
        AppenderRef("DBAsync"),
    ),
)

/**
 * An appender is configured with the <appender> element, which takes two mandatory attributes: name and class.
 * The name attribute specifies the name of the appender whereas the class attribute specifies the fully qualified name of
 * the appender class to instantiate. The <appender> element may contain zero or one <layout> elements, zero or more
 * <encoder> elements and zero or more <filter> elements.
 * Apart from these three common elements, <appender> elements may contain any number of elements corresponding to
 * JavaBean properties of the appender class.
 */
@JacksonXmlRootElement(localName = "appender")
data class Appender(
    @field:JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    val className: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "queueSize")
    val queueSize: String? = null,
    @field:JacksonXmlProperty(isAttribute = true, localName = "discardingThreshold")
    val discardingThreshold: String? = null,
    @field:JacksonXmlProperty(localName = "rollingPolicy")
    val rollingPolicy: RollingPolicy? = null,
    @JacksonXmlProperty(localName = "encoder")
    @JacksonXmlElementWrapper(useWrapping = false)
    val encoder: List<Encoder>? = emptyList(),
    @JacksonXmlProperty(localName = "filter")
    @JacksonXmlElementWrapper(useWrapping = false)
    val levelFilter: LevelFilter? = null,
    @field:JacksonXmlProperty(localName = "dir")
    val dir: String? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(useWrapping = false)
    val appenderRef: List<AppenderRef>? = emptyList(),
)

data class AppenderRef(
    @field:JacksonXmlProperty(isAttribute = true, localName = "ref")
    val ref: String,
)

data class Encoder(
    @field:JacksonXmlProperty(localName = "pattern")
    val pattern: String = "%.-1p [%-30c{1}] [%d{MM:dd:YYYY HH:mm:ss, America/Los_Angeles}]: %m %X%n",
)

/**
 * A <logger> element takes exactly one mandatory name attribute, an optional level attribute, and an optional additivity
 * attribute, admitting the values true or false. The value of the level attribute admitting one of the case-insensitive
 * string values TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. The special case-insensitive value INHERITED, or its synonym
 * NULL, will force the level of the logger to be inherited from higher up in the hierarchy. This comes in handy if you
 * set the level of a logger and later decide that it should inherit its level.
 * The <logger> element may contain zero or more <appender-ref> elements.
 */
@JacksonXmlRootElement(localName = "logger")
data class Logger(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,
    @JacksonXmlProperty(isAttribute = true, localName = "level")
    val level: String? = null,
    @JacksonXmlProperty(isAttribute = true, localName = "additivity")
    val additivity: Boolean? = null,
    @field:JacksonXmlProperty(localName = "appender-ref")
    @JacksonXmlElementWrapper(useWrapping = false)
    val appenderRef: List<AppenderRef>? = emptyList(),
)

/**
 * The <filter> element filters events based on exact level matching.
 * If the event's level is equal to the configured level, the filter accepts or denies the event, depending on the
 * configuration of the onMatch and onMismatch properties.
 */
@JacksonXmlRootElement(localName = "filter")
data class LevelFilter(
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    val className: String = "ch.qos.logback.classic.filter.LevelFilter",
    @field:JacksonXmlProperty(localName = "level")
    val level: String,
    @field:JacksonXmlProperty(localName = "onMatch")
    val onMatch: String? = "ACCEPT",
    @field:JacksonXmlProperty(localName = "onMismatch")
    val onMismatch: String? = "DENY",
)

/**
 * Sometimes you may wish to archive files essentially by date but at the same time limit the size of each log file,
 * in particular if post-processing tools impose size limits on the log files.
 * In order to address this requirement, logback ships with SizeAndTimeBasedRollingPolicy.
 *
 * Note the "%i" conversion token in addition to "%d". Both the %i and %d tokens are mandatory.
 * Each time the current log file reaches maxFileSize before the current time period ends,
 * it will be archived with an increasing index, starting at 0.
 *
 * Size and time based archiving supports deletion of old archive files.
 * You need to specify the number of periods to preserve with the maxHistory property.
 * When your application is stopped and restarted, logging will continue at the correct location,
 * i.e. at the largest index number for the current period.
 */
@JacksonXmlRootElement(localName = "rollingPolicy")
data class RollingPolicy(
    @field:JacksonXmlProperty(isAttribute = true, localName = "class")
    val className: String = "ch.qos.logback.core.rolling.RollingFileAppender",
    @field:JacksonXmlProperty(localName = "fileNamePattern")
    val fileNamePattern: String = "\${ROOT}\\\\AdditionalLogs.%d{yyyy-MM-dd}.%i.log",
    @field:JacksonXmlProperty(localName = "maxFileSize")
    val maxFileSize: String = "10MB",
    @field:JacksonXmlProperty(localName = "totalSizeCap")
    val totalSizeCap: String = "1GB",
    @field:JacksonXmlProperty(localName = "maxHistory")
    val maxHistory: String = "5",
)
