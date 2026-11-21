package com.aether.data.thread

import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomTrackedThreadRepository @Inject constructor(
    private val dao: TrackedThreadDao,
) : TrackedThreadRepository {

    override fun observeActive(): Flow<List<TrackedThread>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): TrackedThread? =
        dao.getById(id)?.toDomain()

    override suspend fun getByTopic(topic: String): TrackedThread? =
        dao.getByTopic(topic)?.toDomain()

    override suspend fun save(thread: TrackedThread) {
        dao.upsert(thread.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}
