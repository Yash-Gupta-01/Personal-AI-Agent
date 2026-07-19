package com.aether.data.memory

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.data.AetherDatabase
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Exercises the storage layer against real SQLite, in memory.
 *
 * These run on a device rather than the JVM because Room generates and
 * executes actual SQL — the queries and the schema are the things worth
 * testing, and a fake would test neither.
 */
@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var database: AetherDatabase
    private lateinit var repository: RoomMemoryRepository

    private val now: Instant = Instant.parse("2026-07-19T10:15:30Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AetherDatabase::class.java,
        ).build()
        repository = RoomMemoryRepository(database.memoryDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun savedMemorySurvivesRoundTripUnchanged() = runTest {
        val memory = Memory.capture("Call the dentist", MemorySource.MANUAL_VOICE, now)

        repository.save(memory)

        val stored = repository.observeActive().first().single()
        assertEquals(memory.id, stored.id)
        assertEquals(memory.content, stored.content)
        assertEquals(memory.type, stored.type)
        // Provenance and timestamps must survive: both drive later behaviour.
        assertEquals(MemorySource.MANUAL_VOICE, stored.source)
        assertEquals(now, stored.createdAt)
    }

    @Test
    fun memoriesAreReturnedNewestFirst() = runTest {
        val older = Memory.capture("Older", MemorySource.MANUAL_TEXT, now)
        val newer = Memory.capture("Newer", MemorySource.MANUAL_TEXT, now.plusSeconds(60))

        repository.save(older)
        repository.save(newer)

        val contents = repository.observeActive().first().map { it.content }
        assertEquals(listOf("Newer", "Older"), contents)
    }

    @Test
    fun savingSameIdReplacesRatherThanDuplicates() = runTest {
        val original = Memory.capture("Draft", MemorySource.MANUAL_TEXT, now)
        repository.save(original)

        repository.save(original.withContent("Final", now.plusSeconds(30)))

        val stored = repository.observeActive().first()
        assertEquals(1, stored.size)
        assertEquals("Final", stored.single().content)
    }

    @Test
    fun deletedMemoryDisappears() = runTest {
        val memory = Memory.capture("Temporary", MemorySource.MANUAL_TEXT, now)
        repository.save(memory)

        repository.delete(memory.id)

        assertTrue(repository.observeActive().first().isEmpty())
    }

    @Test
    fun archivedMemoriesAreExcludedFromActive() = runTest {
        val archived = Memory.capture("Done with this", MemorySource.MANUAL_TEXT, now)
            .copy(status = MemoryStatus.ARCHIVED)

        repository.save(archived)

        assertTrue(repository.observeActive().first().isEmpty())
    }
}
