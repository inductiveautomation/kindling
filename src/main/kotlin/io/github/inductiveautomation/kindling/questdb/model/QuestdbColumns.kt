package io.github.inductiveautomation.kindling.questdb.model

import io.github.inductiveautomation.kindling.utils.ColumnList
import org.jdesktop.swingx.renderer.DefaultTableRenderer

@Suppress("unused")
object QuestdbTableColumns : ColumnList<QuestdbTableEntry>() {
    val id by column { it.id }
    val tableName by column(
        column = {
            cellRenderer = DefaultTableRenderer(Any?::toString)
        },
        value = QuestdbTableEntry::tableName,
    )
    val designatedTimestamp by column { it.designatedTimestamp }
    val partitionBy by column { it.partitionBy }
    val maxUncommittedRows by column { it.maxUncommittedRows }
    val o3MaxLag by column { it.o3MaxLag }
    val walEnabled by column { it.walEnabled }
    val directoryName by column { it.directoryName }
    val dedup by column { it.dedup }
    val ttlValue by column { it.ttlValue }
    val ttlUnit by column { it.ttlUnit }
    val matView by column { it.matView }
}

@Suppress("unused")
object QuestdbAnnotationsColumns : ColumnList<QuestdbAnnotationsEntry>() {
    val annotationId by column { it.annotationId }
    val nodeId by column { it.nodeId }
    val type by column { it.type }
    val startAt by column { it.startAt }
    val endAt by column { it.endAt }
    val notes by column { it.notes }
    val isDeleted by column { it.isDeleted }
    val changedAt by column { it.changedAt }
}

@Suppress("unused")
object QuestdbDatapointsColumns : ColumnList<QuestdbDatapointsEntry>() {
    val nodeId by column { it.nodeId }
    val valueTime by column { it.valueTime }
    val longValue by column { it.longValue }
    val doubleValue by column { it.doubleValue }
    val stringValue by column { it.stringValue }
    val timeValue by column { it.timeValue }
    val qualityCode by column { it.qualityCode }
    val qualityLevel by column { it.qualityLevel }
    val isObserved by column { it.isObserved }
    val snapshotTime by column { it.snapshotTime }
}

@Suppress("unused")
object QuestdbDefinitionsColumns : ColumnList<QuestdbDefinitionsEntry>() {
    val nodeId by column { it.nodeId }
    val source by column { it.source }
    val dataType by column { it.dataType }
    val createdAt by column { it.createdAt }
    val retiredAt by column { it.retiredAt }
}

@Suppress("unused")
object QuestdbMetadataColumns : ColumnList<QuestdbMetadataEntry>() {
    val nodeId by column { it.nodeId }
    val metadata by column { it.metadata }
    val timestamp by column { it.timestamp }
}
