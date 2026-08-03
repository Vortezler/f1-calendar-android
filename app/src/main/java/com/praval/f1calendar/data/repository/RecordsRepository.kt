package com.praval.f1calendar.data.repository

import com.praval.f1calendar.core.Res
import com.praval.f1calendar.core.map
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.RecordsDao
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.mapper.toDomain
import com.praval.f1calendar.data.mapper.toEntity
import com.praval.f1calendar.data.mapper.toLapRecordOrNull
import com.praval.f1calendar.data.remote.ErgastApi
import com.praval.f1calendar.data.remote.apiCall
import com.praval.f1calendar.domain.model.Circuit
import com.praval.f1calendar.domain.model.LapRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RecordSyncProgress(
    val completed: Int,
    val total: Int,
    val running: Boolean,
    val error: String? = null,
)

/**
 * Lap records for every circuit in the championship's history.
 *
 * There is no bulk "records" endpoint, so a record costs one request per circuit — 78 of them. That
 * is done once, spaced out to stay inside the API's rate limit, and then cached for a week: a lap
 * record only changes when a race is actually held there.
 */
@Singleton
class RecordsRepository @Inject constructor(
    private val api: ErgastApi,
    private val recordsDao: RecordsDao,
    private val cacheDao: CacheDao,
) {

    fun observeCircuits(): Flow<List<Circuit>> =
        recordsDao.observeCircuits().map { rows -> rows.map { it.toDomain() } }

    fun observeRecords(): Flow<Map<String, LapRecord>> =
        recordsDao.observeRecords().map { rows ->
            rows.associate { it.circuitId to it.toDomain() }
        }

    suspend fun refreshCircuits(force: Boolean = false): Res<Unit> {
        if (!force && isFresh(CIRCUITS_KEY, CIRCUITS_TTL) && recordsDao.countCircuits() > 0) {
            return Res.Success(Unit)
        }
        return apiCall { api.circuits() }.map { response ->
            val circuits = response.data.circuitTable.circuits.map { it.toEntity() }
            if (circuits.isNotEmpty()) {
                recordsDao.upsertCircuits(circuits)
                markFetched(CIRCUITS_KEY)
            }
        }
    }

    /**
     * Walks the circuits that don't have a fresh record, one request at a time, emitting progress
     * as it goes so the list can fill in visibly rather than blocking on all 78.
     */
    fun syncRecords(force: Boolean = false): Flow<RecordSyncProgress> = flow {
        val ids = recordsDao.circuitIds()
        if (ids.isEmpty()) {
            emit(RecordSyncProgress(0, 0, running = false))
            return@flow
        }

        val pending = if (force) ids else ids.filterNot { isFresh(recordKey(it), RECORD_TTL) }
        var completed = ids.size - pending.size
        if (pending.isEmpty()) {
            emit(RecordSyncProgress(completed, ids.size, running = false))
            return@flow
        }

        emit(RecordSyncProgress(completed, ids.size, running = true))

        var lastError: String? = null
        var consecutiveFailures = 0

        for (id in pending) {
            when (val result = apiCall { api.circuitFastestLaps(id) }) {
                is Res.Success -> {
                    val record = result.data.data.raceTable.races.toLapRecordOrNull(id)
                    // A circuit last raced before 2004 has no lap times at all. Record that it was
                    // checked so it isn't retried on every visit.
                    if (record != null) {
                        recordsDao.upsertRecord(record)
                    } else {
                        recordsDao.deleteRecord(id)
                    }
                    markFetched(recordKey(id))
                    consecutiveFailures = 0
                }

                is Res.Error -> {
                    lastError = result.message
                    consecutiveFailures++
                }

                Res.Loading -> Unit
            }

            completed++
            emit(RecordSyncProgress(completed, ids.size, running = true, error = lastError))

            // Repeated failures usually mean rate limiting or an outage; continuing to hammer makes
            // both worse.
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                emit(RecordSyncProgress(completed, ids.size, running = false, error = lastError))
                return@flow
            }

            delay(REQUEST_SPACING_MS)
        }

        emit(RecordSyncProgress(completed, ids.size, running = false, error = lastError))
    }

    private suspend fun isFresh(key: String, ttlMillis: Long): Boolean {
        val meta = cacheDao.get(key) ?: return false
        return (System.currentTimeMillis() - meta.fetchedAt) in 0..ttlMillis
    }

    private suspend fun markFetched(key: String) =
        cacheDao.put(CacheMetaEntity(key, System.currentTimeMillis()))

    private companion object {
        const val CIRCUITS_KEY = "circuits"
        fun recordKey(circuitId: String) = "laprecord:$circuitId"

        val CIRCUITS_TTL = TimeUnit.DAYS.toMillis(30)
        val RECORD_TTL = TimeUnit.DAYS.toMillis(7)

        /** Jolpica allows a small burst per second; four per second stays well inside it. */
        const val REQUEST_SPACING_MS = 260L
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
