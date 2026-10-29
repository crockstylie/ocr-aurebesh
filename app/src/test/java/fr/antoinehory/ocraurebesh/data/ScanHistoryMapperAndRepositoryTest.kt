package fr.antoinehory.ocraurebesh.data

import fr.antoinehory.ocraurebesh.data.local.dao.ScanHistoryDao
import fr.antoinehory.ocraurebesh.data.local.entity.ScanHistoryEntity
import fr.antoinehory.ocraurebesh.data.local.entity.toDomain
import fr.antoinehory.ocraurebesh.data.local.entity.toEntity
import fr.antoinehory.ocraurebesh.data.repository.ScanHistoryRepository
import fr.antoinehory.ocraurebesh.domain.ScanRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires des fonctions de mapping Room (`toDomain` / `toEntity`)
 * et du [ScanHistoryRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanHistoryMapperAndRepositoryTest {

    @Test
    fun `ScanHistoryEntity toDomain and ScanRecord toEntity preserve all fields`() {
        val originalEntity = ScanHistoryEntity(
            id = 42L,
            translatedText = "OBI WAN KENOBI",
            confidence = 94,
            createdAt = 1730000000000L,
            isFavorite = true,
        )

        val domainRecord = originalEntity.toDomain()
        assertEquals(42L, domainRecord.id)
        assertEquals("OBI WAN KENOBI", domainRecord.translatedText)
        assertEquals(94, domainRecord.confidence)
        assertEquals(1730000000000L, domainRecord.createdAt)
        assertTrue(domainRecord.isFavorite)

        val roundTripEntity = domainRecord.toEntity()
        assertEquals(originalEntity, roundTripEntity)
    }

    @Test
    fun `ScanHistoryRepository saves, filters favorites, deletes and clears records`() = runTest {
        val fakeDao = FakeScanHistoryDao()
        val repository = ScanHistoryRepository(
            scanHistoryDao = fakeDao,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val firstId = repository.saveScan(
            translatedText = "  MILLENNIUM FALCON  ",
            confidence = 115, // Doit être borné à 100
            isFavorite = false,
        )
        val secondId = repository.saveScan(
            translatedText = "DEATH STAR",
            confidence = 88,
            isFavorite = true,
        )

        val allRecords = repository.allScans.first()
        assertEquals(2, allRecords.size)
        val firstSaved = allRecords.first { it.id == firstId }
        assertEquals("MILLENNIUM FALCON", firstSaved.translatedText)
        assertEquals(100, firstSaved.confidence)

        // Vérification du filtre favoris initial
        assertEquals(listOf(secondId), repository.favoriteScans.first().map(ScanRecord::id))

        // Bascule du premier scan en favori
        repository.setFavorite(firstId, true)
        assertEquals(2, repository.favoriteScans.first().size)

        // Suppression individuelle
        repository.deleteScan(secondId)
        assertEquals(listOf(firstId), repository.allScans.first().map(ScanRecord::id))

        // Effacement complet
        repository.clearHistory()
        assertTrue(repository.allScans.first().isEmpty())
    }
}

/**
 * Implémentation Fake en mémoire de [ScanHistoryDao] pour les tests unitaires.
 */
internal class FakeScanHistoryDao : ScanHistoryDao {

    private val recordsFlow = MutableStateFlow<List<ScanHistoryEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ScanHistoryEntity>> =
        recordsFlow.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeFavorites(): Flow<List<ScanHistoryEntity>> =
        recordsFlow.map { list ->
            list.filter { it.isFavorite }.sortedByDescending { it.createdAt }
        }

    override suspend fun insert(entity: ScanHistoryEntity): Long {
        val assignedId = if (entity.id == 0L) nextId++ else entity.id
        val saved = entity.copy(id = assignedId)
        recordsFlow.update { current ->
            current.filterNot { it.id == assignedId } + saved
        }
        return assignedId
    }

    override suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) {
        recordsFlow.update { current ->
            current.map { item ->
                if (item.id == id) item.copy(isFavorite = isFavorite) else item
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        recordsFlow.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun clearAll() {
        recordsFlow.value = emptyList()
    }
}
