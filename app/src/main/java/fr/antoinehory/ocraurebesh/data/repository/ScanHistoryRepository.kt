package fr.antoinehory.ocraurebesh.data.repository

import fr.antoinehory.ocraurebesh.data.local.dao.ScanHistoryDao
import fr.antoinehory.ocraurebesh.data.local.entity.toDomain
import fr.antoinehory.ocraurebesh.data.local.entity.toEntity
import fr.antoinehory.ocraurebesh.domain.ScanRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository gérant la persistance et l'observation des traductions Aurebesh via [ScanHistoryDao].
 *
 * Respecte la séparation entre entités Room ([fr.antoinehory.ocraurebesh.data.local.entity.ScanHistoryEntity])
 * et modèles de domaine immuables ([ScanRecord]).
 */
class ScanHistoryRepository(
    private val scanHistoryDao: ScanHistoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Flux réactif de tous les scans enregistrés, du plus récent au plus ancien.
     */
    val allScans: Flow<List<ScanRecord>> = scanHistoryDao
        .observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    /**
     * Flux réactif des scans marqués comme favoris, du plus récent au plus ancien.
     */
    val favoriteScans: Flow<List<ScanRecord>> = scanHistoryDao
        .observeFavorites()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(ioDispatcher)

    /**
     * Enregistre une nouvelle traduction Aurebesh dans l'historique local.
     */
    suspend fun saveScan(
        translatedText: String,
        confidence: Int,
        isFavorite: Boolean = false
    ): Long = withContext(ioDispatcher) {
        val record = ScanRecord(
            translatedText = translatedText.trim(),
            confidence = confidence.coerceIn(0, 100),
            isFavorite = isFavorite
        )
        scanHistoryDao.insert(record.toEntity())
    }

    /**
     * Inverse ou définit le statut favori d'un enregistrement.
     */
    suspend fun setFavorite(id: Long, isFavorite: Boolean) = withContext(ioDispatcher) {
        scanHistoryDao.updateFavoriteStatus(id = id, isFavorite = isFavorite)
    }

    /**
     * Supprime un enregistrement de l'historique par son identifiant.
     */
    suspend fun deleteScan(id: Long) = withContext(ioDispatcher) {
        scanHistoryDao.deleteById(id)
    }

    /**
     * Supprime l'intégralité de l'historique des scans.
     */
    suspend fun clearHistory() = withContext(ioDispatcher) {
        scanHistoryDao.clearAll()
    }
}
