package com.praval.f1calendar.data.repository

import com.praval.f1calendar.core.Res
import com.praval.f1calendar.core.map
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.RaceDao
import com.praval.f1calendar.data.local.dao.ResultDao
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.mapper.toDomain
import com.praval.f1calendar.data.mapper.toEntityOrNull
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.remote.ErgastApi
import com.praval.f1calendar.data.remote.apiCall
import com.praval.f1calendar.domain.model.QualifyingResult
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.RaceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for calendars and race results.
 *
 * Reads always come from Room, so every screen works offline once it has been visited. Network
 * calls only ever write into Room; the UI observes the database.
 */
@Singleton
class RaceRepository @Inject constructor(
    private val api: ErgastApi,
    private val raceDao: RaceDao,
    private val resultDao: ResultDao,
    private val cacheDao: CacheDao,
    private val settings: SettingsStore,
) {

    fun observeSeason(season: Int): Flow<List<Race>> =
        raceDao.observeSeason(season).map { rows -> rows.map { it.toDomain() } }

    fun observeRace(season: Int, round: Int): Flow<Race?> =
        raceDao.observeRace(season, round).map { it?.toDomain() }

    fun observeResults(season: Int, round: Int): Flow<List<RaceResult>> =
        resultDao.observeResults(season, round).map { rows -> rows.map { it.toDomain() } }

    fun observeQualifying(season: Int, round: Int): Flow<List<QualifyingResult>> =
        resultDao.observeQualifying(season, round).map { rows -> rows.map { it.toDomain() } }

    suspend fun racesForSeason(season: Int): List<Race> =
        raceDao.getSeason(season).map { it.toDomain() }

    suspend fun cachedSeasons(): List<Int> = raceDao.cachedSeasons()

    /**
     * Asks the API which season is live rather than deriving it from the device clock, which would
     * be wrong between the last race of one season and the first of the next.
     * Returns the resolved season number.
     */
    suspend fun refreshCurrentSeason(force: Boolean = false): Res<Int> {
        val known = settings.resolvedCurrentSeason.firstOrZero()
        if (!force && known != 0 && isFresh(currentKey(), SCHEDULE_TTL)) return Res.Success(known)

        return apiCall { api.schedule(ErgastApi.CURRENT) }.map { response ->
            val season = response.data.raceTable.season?.toIntOrNull()
                ?: response.data.raceTable.races.firstOrNull()?.season?.toIntOrNull()
                ?: known
            val races = response.data.raceTable.races.mapNotNull { it.toEntityOrNull() }
            if (races.isNotEmpty() && season != 0) {
                raceDao.replaceSeason(season, races)
                markFetched(scheduleKey(season))
                markFetched(currentKey())
                settings.setResolvedCurrentSeason(season)
            }
            season
        }
    }

    suspend fun refreshSchedule(season: Int, force: Boolean = false): Res<Unit> {
        if (!force && isFresh(scheduleKey(season), SCHEDULE_TTL) && raceDao.countForSeason(season) > 0) {
            return Res.Success(Unit)
        }
        return apiCall { api.schedule(season.toString()) }.map { response ->
            val races = response.data.raceTable.races.mapNotNull { it.toEntityOrNull() }
            // An empty calendar means the season isn't published yet; keep whatever we already have.
            if (races.isNotEmpty()) {
                raceDao.replaceSeason(season, races)
                markFetched(scheduleKey(season))
            }
        }
    }

    suspend fun refreshResults(season: Int, round: Int, force: Boolean = false): Res<Unit> {
        if (!force && resultDao.countResults(season, round) > 0 && isSettled(season, round)) {
            return Res.Success(Unit)
        }
        if (!force && isFresh(resultsKey(season, round), RESULTS_TTL)) return Res.Success(Unit)

        return apiCall { api.raceResults(season.toString(), round) }.map { response ->
            val rows = response.data.raceTable.races.firstOrNull()?.results.orEmpty()
                .mapNotNull { it.toEntityOrNull(season, round) }
            resultDao.replaceResults(season, round, rows)
            markFetched(resultsKey(season, round))
        }
    }

    suspend fun refreshQualifying(season: Int, round: Int, force: Boolean = false): Res<Unit> {
        if (!force && resultDao.countQualifying(season, round) > 0 && isSettled(season, round)) {
            return Res.Success(Unit)
        }
        if (!force && isFresh(qualifyingKey(season, round), RESULTS_TTL)) return Res.Success(Unit)

        return apiCall { api.qualifyingResults(season.toString(), round) }.map { response ->
            val rows = response.data.raceTable.races.firstOrNull()?.qualifyingResults.orEmpty()
                .mapNotNull { it.toEntityOrNull(season, round) }
            resultDao.replaceQualifying(season, round, rows)
            markFetched(qualifyingKey(season, round))
        }
    }

    /**
     * Classifications are provisional for a few hours after the flag while stewards' decisions land,
     * but a day later they are final and never need refetching.
     */
    private suspend fun isSettled(season: Int, round: Int): Boolean {
        val raceUtc = raceDao.getRace(season, round)?.raceUtc ?: return false
        return System.currentTimeMillis() - raceUtc > SETTLED_AFTER
    }

    private suspend fun isFresh(key: String, ttlMillis: Long): Boolean {
        val meta = cacheDao.get(key) ?: return false
        val age = System.currentTimeMillis() - meta.fetchedAt
        return age in 0..ttlMillis
    }

    private suspend fun markFetched(key: String) =
        cacheDao.put(CacheMetaEntity(key, System.currentTimeMillis()))

    private suspend fun Flow<Int>.firstOrZero(): Int = firstOrNull() ?: 0

    companion object {
        private val SCHEDULE_TTL = TimeUnit.HOURS.toMillis(1)
        private val RESULTS_TTL = TimeUnit.HOURS.toMillis(1)
        private val SETTLED_AFTER = TimeUnit.HOURS.toMillis(24)

        private fun currentKey() = "schedule:current"
        private fun scheduleKey(season: Int) = "schedule:$season"
        private fun resultsKey(season: Int, round: Int) = "results:$season:$round"
        private fun qualifyingKey(season: Int, round: Int) = "qualifying:$season:$round"
    }
}
