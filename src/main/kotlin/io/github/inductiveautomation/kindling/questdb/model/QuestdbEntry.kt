package io.github.inductiveautomation.kindling.questdb.model

import io.github.inductiveautomation.kindling.utils.get
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

data class QuestdbTableEntry(
    val id: Int,
    val tableName: String,
    val designatedTimestamp: String,
    val partitionBy: String,
    val maxUncommittedRows: Int,
    val o3MaxLag: Long,
    val walEnabled: Boolean,
    val directoryName: String,
    val dedup: Boolean,
    val ttlValue: Int,
    val ttlUnit: String,
    val matView: Boolean,
) {
    constructor(rs: ResultSet) : this(
        id = rs.get<Int>("id"),
        tableName = rs.get<String>("table_name"),
        designatedTimestamp = rs.get<String>("designatedTimestamp"),
        partitionBy = rs.get<String>("partitionBy"),
        maxUncommittedRows = rs.get<Int>("maxUncommittedRows"),
        o3MaxLag = rs.get<Long>("o3MaxLag"),
        walEnabled = rs.get<Boolean>("walEnabled"),
        directoryName = rs.get<String>("directoryName"),
        dedup = rs.get<Boolean>("dedup"),
        ttlValue = rs.get<Int>("ttlValue"),
        ttlUnit = rs.get<String>("ttlUnit"),
        matView = rs.get<Boolean>("matView"),
    )
}

data class QuestdbAnnotationsEntry(
    val annotationId: UUID,
    val nodeId: String,
    val type: String,
    val startAt: Timestamp,
    val endAt: Timestamp,
    val notes: String,
    val isDeleted: Boolean,
    val changedAt: Timestamp,
) {
    constructor(rs: ResultSet) : this(
        annotationId = rs.get<UUID>("annotation_id"),
        nodeId = rs.get<String>("node_id"),
        type = rs.get<String>("type"),
        startAt = rs.get<Timestamp>("start_at"),
        endAt = rs.get<Timestamp>("end_at"),
        notes = rs.get<String>("notes"),
        isDeleted = rs.get<Boolean>("is_deleted"),
        changedAt = rs.get<Timestamp>("changed_at"),
    )
}

data class QuestdbDatapointsEntry(
    val nodeId: String,
    val valueTime: Timestamp,
    val longValue: Long?,
    val doubleValue: Double?,
    val stringValue: String?,
    val timeValue: Timestamp?,
    val qualityCode: Int,
    val qualityLevel: Int,
    val isObserved: Boolean,
    val snapshotTime: Timestamp,
) {
    constructor(rs: ResultSet) : this(
        nodeId = rs.get<String>("node_id"),
        valueTime = rs.get<Timestamp>("value_time"),
        longValue = rs.get<Long?>("long_value"),
        doubleValue = rs.get<Double?>("double_value"),
        stringValue = rs.get<String?>("string_value"),
        timeValue = rs.get<Timestamp?>("time_value"),
        qualityCode = rs.get<Int>("quality_code"),
        qualityLevel = rs.get<Int>("quality_level"),
        isObserved = rs.get<Boolean>("is_observed"),
        snapshotTime = rs.get<Timestamp>("snapshot_time"),
    )
}

data class QuestdbDefinitionsEntry(
    val nodeId: String,
    val source: String,
    val dataType: Int,
    val createdAt: Timestamp,
    val retiredAt: Timestamp?,
) {
    constructor(rs: ResultSet) : this(
        nodeId = rs.get<String>("node_id"),
        source = rs.get<String>("source"),
        dataType = rs.get<Int>("data_type"),
        createdAt = rs.get<Timestamp>("created_at"),
        retiredAt = rs.get<Timestamp?>("retired_at"),
    )
}

data class QuestdbMetadataEntry(
    val nodeId: String,
    val metadata: String,
    val timestamp: Timestamp,
) {
    constructor(rs: ResultSet) : this(
        nodeId = rs.get<String>("node_id"),
        metadata = rs.get<String>("metadata"),
        timestamp = rs.get<Timestamp>("timestamp"),
    )
}
