package fr.antoinehory.ocraurebesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.antoinehory.ocraurebesh.domain.ScanRecord

/**
 * Entité Room stockant l'historique des traductions Aurebesh dans la table `scan_history`.
 */
@Entity(
    tableName = "scan_history",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["is_favorite"])
    ]
)
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "translated_text")
    val translatedText: String,

    @ColumnInfo(name = "confidence")
    val confidence: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)

/**
 * Convertit une entité Room [ScanHistoryEntity] en modèle de domaine [ScanRecord].
 */
fun ScanHistoryEntity.toDomain(): ScanRecord = ScanRecord(
    id = id,
    translatedText = translatedText,
    confidence = confidence,
    createdAt = createdAt,
    isFavorite = isFavorite
)

/**
 * Convertit un modèle de domaine [ScanRecord] en entité Room [ScanHistoryEntity].
 */
fun ScanRecord.toEntity(): ScanHistoryEntity = ScanHistoryEntity(
    id = id,
    translatedText = translatedText,
    confidence = confidence,
    createdAt = createdAt,
    isFavorite = isFavorite
)
