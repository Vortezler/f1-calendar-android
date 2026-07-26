package com.praval.f1calendar.data.repository

import com.praval.f1calendar.core.Res
import com.praval.f1calendar.core.map
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.StandingsDao
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.mapper.toDomain
import com.praval.f1calendar.data.mapper.toEntityOrNull
import com.praval.f1calendar.data.remote.ErgastApi
import com.praval.f1calendar.data.remote.apiCall
import com.praval.f1calendar.domain.model.ConstructorStanding
import com.praval.f1calendar.domain.model.DriverStanding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandingsRepository @Inject constructor(
    private val api: ErgastApi,
    private val standingsDao: StandingsDao,
    private val cacheDao: CacheDao,
) {

    fun observeDrivers(season: Int): Flow<List<DriverStanding>> =
        standingsDao.observeDrivers(season).map { rows -> rows.map { it.toDomain() } }

    fun observeConstructors(season: Int): Flow<List<ConstructorStanding>> =
        standingsDao.observeConstructors(season).map { rows -> rows.map { it.toDomain() } }

    suspend fun refreshDrivers(season: Int, force: Boolean = false): Res<Unit> {
        val key = "driverstandings:$season"
        if (!force && isFresh(key)) return Res.Success(Unit)
        return apiCall { api.driverStandings(season.toString()) }.map { response ->
            val rows = response.data.standingsTable.standingsLists.firstOrNull()
                ?.driverStandings.orEmpty()
                .mapNotNull { it.toEntityOrNull(season) }
            // A season with no rounds run yet returns an empty list; don't wipe a good cache for it.
            if (rows.isNotEmpty()) {
                standingsDao.replaceDrivers(season, rows)
                markFetched(key)
            }
        }
    }

    suspend fun refreshConstructors(season: Int, force: Boolean = false): Res<Unit> {
        val key = "constructorstandings:$season"
        if (!force && isFresh(key)) return Res.Success(Unit)
        return apiCall { api.constructorStandings(season.toString()) }.map { response ->
            val rows = response.data.standingsTable.standingsLists.firstOrNull()
                ?.constructorStandings.orEmpty()
                .mapNotNull { it.toEntityOrNull(season) }
            if (rows.isNotEmpty()) {
                standingsDao.replaceConstructors(season, rows)
                markFetched(key)
            }
        }
    }

    private suspend fun isFresh(key: String): Boolean {
        val meta = cacheDao.get(key) ?: return false
        return (System.currentTimeMillis() - meta.fetchedAt) in 0..STANDINGS_TTL
    }

    private suspend fun markFetched(key: String) =
        cacheDao.put(CacheMetaEntity(key, System.currentTimeMillis()))

    companion object {
        private val STANDINGS_TTL = TimeUnit.HOURS.toMillis(6)
    }
}
