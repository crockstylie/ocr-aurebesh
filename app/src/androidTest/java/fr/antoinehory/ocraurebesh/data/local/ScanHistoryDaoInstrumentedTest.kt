package fr.antoinehory.ocraurebesh.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.antoinehory.ocraurebesh.data.local.dao.ScanHistoryDao
import fr.antoinehory.ocraurebesh.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentés de [ScanHistoryDao] avec une base SQLite Room en mémoire (`inMemoryDatabaseBuilder`).
 */
@RunWith(AndroidJUnit4::class)
class ScanHistoryDaoInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ScanHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.scanHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertObserveToggleFavoriteAndClear() = runBlocking {
        val id1 = dao.insert(
            ScanHistoryEntity(
                translatedText = "CORUSCANT",
                confidence = 92,
                createdAt = 1000L,
                isFavorite = false,
            )
        )
        val id2 = dao.insert(
            ScanHistoryEntity(
                translatedText = "MANDALORE",
                confidence = 97,
                createdAt = 2000L,
                isFavorite = true,
            )
        )

        val allItems = dao.observeAll().first()
        assertEquals(2, allItems.size)
        // Trié du plus récent (createdAt = 2000L) au plus ancien (1000L)
        assertEquals("MANDALORE", allItems[0].translatedText)
        assertEquals("CORUSCANT", allItems[1].translatedText)

        // Favoris uniquement
        val initialFavorites = dao.observeFavorites().first()
        assertEquals(1, initialFavorites.size)
        assertEquals(id2, initialFavorites[0].id)

        // Bascule favori sur CORUSCANT
        dao.updateFavoriteStatus(id1, true)
        assertEquals(2, dao.observeFavorites().first().size)

        // Suppression individuelle
        dao.deleteById(id2)
        assertEquals(1, dao.observeAll().first().size)

        // Vidage complet
        dao.clearAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }
}
