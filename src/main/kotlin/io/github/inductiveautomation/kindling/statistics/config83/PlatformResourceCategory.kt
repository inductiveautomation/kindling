package io.github.inductiveautomation.kindling.statistics.config83

@Suppress("unused")
enum class PlatformResourceCategory {
    ALARM_JOURNAL,
    AUDIT_PROFILE,
    COBRANDING,
    DATABASE_CONNECTION,
    DATABASE_DRIVER,
    DATABASE_TRANSLATOR,
    EDGE_SYNC_SETTINGS,
    EDGE_SYSTEM_PROPERTIES,
    EMAIL_PROFILE,
    GATEWAY_NETWORK_INCOMING,
    GATEWAY_NETWORK_OUTGOING,
    GATEWAY_NETWORK_PROXY_RULES,
    GATEWAY_NETWORK_QUEUE_SETTINGS,
    GENERAL_ALARM_SETTINGS,
    HOLIDAY,
    IDENTITY_PROVIDER,
    IMAGES,
    KEYBOARD_LAYOUT {
        override val folderName: String = "keyboard_layout" // why??
    },
    LOCAL_SYSTEM_PROPERTIES,
    METRICS_DASHBOARD,
    OPC_CONNECTION,
    QUICKSTART,
    ROSTER_CONFIG,
    SCHEDULE,
    SECURITY_LEVELS,
    SECURITY_PROPERTIES,
    SECURITY_ZONE,
    STORE_AND_FORWARD_ENGINE,
    SYSTEM_PROPERTIES,
    TAG_GROUP,
    TAG_PROVIDER,
    TRANSLATIONS,
    USER_SOURCE,
    ;

    open val folderName: String = name.replace("_", "-").lowercase()
}
