package fr.antoinehory.ocraurebesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.antoinehory.ocraurebesh.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux données (DAO) pour l'historique et les favoris des scans Aurebesh.
 */
@Dao
interface ScanHistoryDao {

    /**
     * Observe l'intégralité de l'historique des scans, du plus récent au plus ancien.
     */
    @Query("SELECT * FROM scan_history ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>

    /**
     * Observe uniquement les scans marqués comme favoris, du plus récent au plus ancien.
     */
    @Query("SELECT * FROM scan_history WHERE is_favorite = 1 ORDER BY created_at DESC")
    fun observeFavorites(): Flow<List<ScanHistoryEntity>>

    /**
     * Insère ou remplace un enregistrement de scan et retourne son identifiant généré.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity): Long

    /**
     * Met à jour le statut favori d'un scan donné.
     */
    @Query("UPDATE scan_history SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    /**
     * Supprime un scan spécifique par son identifiant.
     */
    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Vide entièrement la table d'historique des scans.
     */
    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
