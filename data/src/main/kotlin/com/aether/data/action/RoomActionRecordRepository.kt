package com.aether.data.action

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomActionRecordRepository @Inject constructor(
    private val dao: ActionRecordDao,
) : ActionRecordRepository {

    override fun observePending(): Flow<List<ActionRecord>> =
        dao.observePending().map { entities -> entities.map { it.toDomain() } }

    override fun observeAll(): Flow<List<ActionRecord>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observePendingFollowUps(): Flow<List<ActionRecord>> =
        dao.observePendingFollowUps().map { entities -> entities.map { it.toDomain() } }

    override fun observeForThread(threadId: String): Flow<List<ActionRecord>> =
        dao.observeForThread(threadId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getLatestPendingFollowUp(): ActionRecord? =
        dao.getLatestPendingFollowUp()?.toDomain()

    override suspend fun getById(id: String): ActionRecord? =
        dao.getById(id)?.toDomain()

    override suspend fun save(record: ActionRecord) {
        dao.upsert(record.toEntity())
    }

    override suspend fun countProactivePostedInWindow(start: java.time.Instant, end: java.time.Instant): Int =
        dao.countPostedInWindow(start.toEpochMilli(), end.toEpochMilli())

    override suspend fun getLastProactivePostedTimestamp(): java.time.Instant? =
        dao.getLastPostedTimestamp()?.let { java.time.Instant.ofEpochMilli(it) }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}
