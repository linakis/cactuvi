package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.StreamSourceDao
import com.cactuvi.app.data.db.entities.StreamSourceEntity
import com.cactuvi.app.data.models.StreamSource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SourceManager.
 *
 * Tests cover:
 * - Getting all sources
 * - Getting active source
 * - Setting active source
 * - Adding/updating/deleting sources
 * - Observing active source flow
 * - Source event emission (SourceAdded, SourceActivated, SourceUpdated, SourceDeleted)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sourceManager: SourceManager
    private lateinit var mockDatabase: AppDatabase
    private lateinit var mockContext: Context
    private lateinit var mockStreamSourceDao: StreamSourceDao
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup mocks
        mockDatabase = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockStreamSourceDao = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        // Wire up SharedPreferences
        every { mockContext.getSharedPreferences("source_prefs", Context.MODE_PRIVATE) } returns
            mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        // Wire up database
        every { mockDatabase.streamSourceDao() } returns mockStreamSourceDao

        sourceManager = SourceManager(mockContext, mockDatabase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== TEST DATA ==========

    private fun createTestSourceEntity(
        id: String = "source-1",
        nickname: String = "Test Source",
        isActive: Boolean = false,
    ): StreamSourceEntity =
        StreamSourceEntity(
            id = id,
            nickname = nickname,
            server = "http://example.com",
            username = "user",
            password = "pass",
            isActive = isActive,
            isPrimary = false,
            createdAt = System.currentTimeMillis(),
            lastUsed = null,
        )

    private fun createTestSource(
        id: String = "source-1",
        nickname: String = "Test Source",
        isActive: Boolean = false,
    ): StreamSource =
        StreamSource(
            id = id,
            nickname = nickname,
            server = "http://example.com",
            username = "user",
            password = "pass",
            isActive = isActive,
            isPrimary = false,
            createdAt = System.currentTimeMillis(),
            lastUsed = null,
        )

    // ========== GET ALL SOURCES ==========

    @Test
    fun `getAllSources returns empty list when no sources exist`() = runTest {
        // Given: No sources in database
        coEvery { mockStreamSourceDao.getAll() } returns emptyList()

        // When
        val result = sourceManager.getAllSources()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockStreamSourceDao.getAll() }
    }

    @Test
    fun `getAllSources returns all sources from database`() = runTest {
        // Given: Multiple sources exist
        val entities =
            listOf(
                createTestSourceEntity("1", "Source 1"),
                createTestSourceEntity("2", "Source 2"),
                createTestSourceEntity("3", "Source 3"),
            )
        coEvery { mockStreamSourceDao.getAll() } returns entities

        // When
        val result = sourceManager.getAllSources()

        // Then
        assertEquals(3, result.size)
        assertEquals("Source 1", result[0].nickname)
        assertEquals("Source 2", result[1].nickname)
        assertEquals("Source 3", result[2].nickname)
    }

    // ========== GET ACTIVE SOURCE ==========

    @Test
    fun `getActiveSource returns null when no active source`() = runTest {
        // Given: No active source
        coEvery { mockStreamSourceDao.getActive() } returns null

        // When
        val result = sourceManager.getActiveSource()

        // Then
        assertNull(result)
    }

    @Test
    fun `getActiveSource returns active source`() = runTest {
        // Given: Active source exists
        val activeEntity = createTestSourceEntity("active-1", "Active Source", isActive = true)
        coEvery { mockStreamSourceDao.getActive() } returns activeEntity

        // When
        val result = sourceManager.getActiveSource()

        // Then
        assertNotNull(result)
        assertEquals("active-1", result?.id)
        assertEquals("Active Source", result?.nickname)
        assertTrue(result?.isActive == true)
    }

    // ========== SET ACTIVE SOURCE ==========

    @Test
    fun `setActiveSource updates database and SharedPreferences`() = runTest {
        // Given
        val sourceId = "new-active-source"

        // When
        sourceManager.setActiveSource(sourceId)

        // Then
        coVerify(exactly = 1) { mockStreamSourceDao.setActive(sourceId) }
        coVerify(exactly = 1) { mockEditor.putString("active_source_id", sourceId) }
        coVerify(exactly = 1) { mockEditor.apply() }
    }

    // ========== ADD SOURCE ==========

    @Test
    fun `addSource inserts source to database`() = runTest {
        // Given
        val newSource = createTestSource("new-1", "New Source")
        val insertedSlot = slot<StreamSourceEntity>()
        coEvery { mockStreamSourceDao.insert(capture(insertedSlot)) } just Runs

        // When
        sourceManager.addSource(newSource)

        // Then
        coVerify(exactly = 1) { mockStreamSourceDao.insert(any()) }
        assertEquals("new-1", insertedSlot.captured.id)
        assertEquals("New Source", insertedSlot.captured.nickname)
    }

    // ========== UPDATE SOURCE ==========

    @Test
    fun `updateSource updates existing source in database`() = runTest {
        // Given
        val existingSource = createTestSource("existing-1", "Updated Name")
        val updatedSlot = slot<StreamSourceEntity>()
        coEvery { mockStreamSourceDao.update(capture(updatedSlot)) } just Runs

        // When
        sourceManager.updateSource(existingSource)

        // Then
        coVerify(exactly = 1) { mockStreamSourceDao.update(any()) }
        assertEquals("existing-1", updatedSlot.captured.id)
        assertEquals("Updated Name", updatedSlot.captured.nickname)
    }

    // ========== DELETE SOURCE ==========

    @Test
    fun `deleteSource removes source from database`() = runTest {
        // Given: Source exists
        val sourceEntity = createTestSourceEntity("delete-1", "To Delete")
        coEvery { mockStreamSourceDao.getById("delete-1") } returns sourceEntity
        coEvery { mockStreamSourceDao.delete(any()) } just Runs

        // When
        sourceManager.deleteSource("delete-1")

        // Then
        coVerify(exactly = 1) { mockStreamSourceDao.getById("delete-1") }
        coVerify(exactly = 1) { mockStreamSourceDao.delete(sourceEntity) }
    }

    @Test
    fun `deleteSource does nothing when source not found`() = runTest {
        // Given: Source doesn't exist
        coEvery { mockStreamSourceDao.getById("nonexistent") } returns null

        // When
        sourceManager.deleteSource("nonexistent")

        // Then
        coVerify(exactly = 1) { mockStreamSourceDao.getById("nonexistent") }
        coVerify(exactly = 0) { mockStreamSourceDao.delete(any()) }
    }

    // ========== ACTIVE SOURCE FLOW ==========

    @Test
    fun `getActiveSourceFlow emits updates when source changes`() = runTest {
        // Given: Flow emits active source
        val activeEntity = createTestSourceEntity("flow-1", "Flow Source", isActive = true)
        every { mockStreamSourceDao.getActiveFlow() } returns flowOf(activeEntity)

        // When
        val result = sourceManager.getActiveSourceFlow().first()

        // Then
        assertNotNull(result)
        assertEquals("flow-1", result?.id)
        assertEquals("Flow Source", result?.nickname)
    }

    @Test
    fun `getActiveSourceFlow emits null when no active source`() = runTest {
        // Given: No active source
        every { mockStreamSourceDao.getActiveFlow() } returns flowOf(null)

        // When
        val result = sourceManager.getActiveSourceFlow().first()

        // Then
        assertNull(result)
    }

    // ========== SOURCE EVENT EMISSION ==========

    @Test
    fun `addSource emits SourceAdded event`() = runTest {
        // Given
        val newSource = createTestSource("new-1", "New Source")
        coEvery { mockStreamSourceDao.insert(any()) } just Runs

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When
        sourceManager.addSource(newSource)
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events[0] is SourceEvent.SourceAdded)
        assertEquals("new-1", (events[0] as SourceEvent.SourceAdded).sourceId)

        job.cancel()
    }

    @Test
    fun `setActiveSource emits SourceActivated event`() = runTest {
        // Given
        val sourceId = "active-source-1"
        coEvery { mockStreamSourceDao.setActive(any()) } just Runs

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When
        sourceManager.setActiveSource(sourceId)
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events[0] is SourceEvent.SourceActivated)
        assertEquals(sourceId, (events[0] as SourceEvent.SourceActivated).sourceId)

        job.cancel()
    }

    @Test
    fun `updateSource emits SourceUpdated event`() = runTest {
        // Given
        val updatedSource = createTestSource("update-1", "Updated Source")
        coEvery { mockStreamSourceDao.update(any()) } just Runs

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When
        sourceManager.updateSource(updatedSource)
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events[0] is SourceEvent.SourceUpdated)
        assertEquals("update-1", (events[0] as SourceEvent.SourceUpdated).sourceId)

        job.cancel()
    }

    @Test
    fun `deleteSource emits SourceDeleted event when source exists`() = runTest {
        // Given
        val sourceEntity = createTestSourceEntity("delete-1", "To Delete")
        coEvery { mockStreamSourceDao.getById("delete-1") } returns sourceEntity
        coEvery { mockStreamSourceDao.delete(any()) } just Runs

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When
        sourceManager.deleteSource("delete-1")
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events[0] is SourceEvent.SourceDeleted)
        assertEquals("delete-1", (events[0] as SourceEvent.SourceDeleted).sourceId)

        job.cancel()
    }

    @Test
    fun `deleteSource does not emit event when source not found`() = runTest {
        // Given
        coEvery { mockStreamSourceDao.getById("nonexistent") } returns null

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When
        sourceManager.deleteSource("nonexistent")
        testScheduler.advanceUntilIdle()

        // Then
        assertEquals(0, events.size)

        job.cancel()
    }

    @Test
    fun `multiple operations emit correct sequence of events`() = runTest {
        // Given
        val source1 = createTestSource("source-1", "Source 1")
        val source2 = createTestSource("source-2", "Source 2")
        coEvery { mockStreamSourceDao.insert(any()) } just Runs
        coEvery { mockStreamSourceDao.setActive(any()) } just Runs
        coEvery { mockStreamSourceDao.update(any()) } just Runs

        val events = mutableListOf<SourceEvent>()
        val job = launch { sourceManager.sourceEvents.toList(events) }

        // When: Add source, activate it, update it
        sourceManager.addSource(source1)
        testScheduler.advanceUntilIdle()

        sourceManager.setActiveSource("source-1")
        testScheduler.advanceUntilIdle()

        sourceManager.updateSource(source1.copy(nickname = "Updated Source 1"))
        testScheduler.advanceUntilIdle()

        sourceManager.addSource(source2)
        testScheduler.advanceUntilIdle()

        // Then: Verify event sequence
        assertEquals(4, events.size)
        assertTrue(events[0] is SourceEvent.SourceAdded)
        assertEquals("source-1", (events[0] as SourceEvent.SourceAdded).sourceId)

        assertTrue(events[1] is SourceEvent.SourceActivated)
        assertEquals("source-1", (events[1] as SourceEvent.SourceActivated).sourceId)

        assertTrue(events[2] is SourceEvent.SourceUpdated)
        assertEquals("source-1", (events[2] as SourceEvent.SourceUpdated).sourceId)

        assertTrue(events[3] is SourceEvent.SourceAdded)
        assertEquals("source-2", (events[3] as SourceEvent.SourceAdded).sourceId)

        job.cancel()
    }
}
