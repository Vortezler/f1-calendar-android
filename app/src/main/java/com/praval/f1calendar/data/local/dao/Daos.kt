package com.praval.f1calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.local.entity.ConstructorStandingEntity
import com.praval.f1calendar.data.local.entity.DriverStandingEntity
import com.praval.f1calendar.data.local.entity.QualifyingResultEntity
import com.praval.f1calendar.data.local.entity.RaceEntity
import com.praval.f1calendar.data.local.entity.RaceResultEntity
import com.praval.f1calendar.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RaceDao {

    @Query("SELECT * FROM races WHERE season = :season ORDER BY round ASC")
    fun observeSeason(season: Int): Flow<List<RaceEntity>>

    @Query("SELECT * FROM races WHERE season = :season ORDER BY round ASC")
    suspend fun getSeason(season: Int): List<RaceEntity>

    @Query("SELECT * FROM races WHERE season = :season AND round = :round")
    fun observeRace(season: Int, round: Int): Flow<RaceEntity?>

    @Query("SELECT * FROM races WHERE season = :season AND round = :round")
    suspend fun getRace(season: Int, round: Int): RaceEntity?

    @Query("SELECT COUNT(*) FROM races WHERE season = :season")
    suspend fun countForSeason(season: Int): Int

    @Query("SELECT DISTINCT season FROM races ORDER BY season DESC")
    suspend fun cachedSeasons(): List<Int>

    /**
     * Replaces the season wholesale. A calendar can lose a round (cancellations happen), so stale
     * rows are cleared rather than merged over.
     */
    @Transaction
    suspend fun replaceSeason(season: Int, races: List<RaceEntity>) {
        deleteSeason(season)
        insertAll(races)
    }

    @Query("DELETE FROM races WHERE season = :season")
    suspend fun deleteSeason(season: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(races: List<RaceEntity>)
}

@Dao
interface ResultDao {

    @Query("SELECT * FROM race_results WHERE season = :season AND round = :round ORDER BY position ASC")
    fun observeResults(season: Int, round: Int): Flow<List<RaceResultEntity>>

    @Query("SELECT COUNT(*) FROM race_results WHERE season = :season AND round = :round")
    suspend fun countResults(season: Int, round: Int): Int

    @Transaction
    suspend fun replaceResults(season: Int, round: Int, results: List<RaceResultEntity>) {
        deleteResults(season, round)
        insertResults(results)
    }

    @Query("DELETE FROM race_results WHERE season = :season AND round = :round")
    suspend fun deleteResults(season: Int, round: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<RaceResultEntity>)

    @Query("SELECT * FROM qualifying_results WHERE season = :season AND round = :round ORDER BY position ASC")
    fun observeQualifying(season: Int, round: Int): Flow<List<QualifyingResultEntity>>

    @Query("SELECT COUNT(*) FROM qualifying_results WHERE season = :season AND round = :round")
    suspend fun countQualifying(season: Int, round: Int): Int

    @Transaction
    suspend fun replaceQualifying(season: Int, round: Int, results: List<QualifyingResultEntity>) {
        deleteQualifying(season, round)
        insertQualifying(results)
    }

    @Query("DELETE FROM qualifying_results WHERE season = :season AND round = :round")
    suspend fun deleteQualifying(season: Int, round: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQualifying(results: List<QualifyingResultEntity>)
}

@Dao
interface StandingsDao {

    @Query("SELECT * FROM driver_standings WHERE season = :season ORDER BY position ASC")
    fun observeDrivers(season: Int): Flow<List<DriverStandingEntity>>

    @Query("SELECT * FROM constructor_standings WHERE season = :season ORDER BY position ASC")
    fun observeConstructors(season: Int): Flow<List<ConstructorStandingEntity>>

    @Transaction
    suspend fun replaceDrivers(season: Int, rows: List<DriverStandingEntity>) {
        deleteDrivers(season)
        insertDrivers(rows)
    }

    @Transaction
    suspend fun replaceConstructors(season: Int, rows: List<ConstructorStandingEntity>) {
        deleteConstructors(season)
        insertConstructors(rows)
    }

    @Query("DELETE FROM driver_standings WHERE season = :season")
    suspend fun deleteDrivers(season: Int)

    @Query("DELETE FROM constructor_standings WHERE season = :season")
    suspend fun deleteConstructors(season: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrivers(rows: List<DriverStandingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConstructors(rows: List<ConstructorStandingEntity>)
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE season = :season AND round = :round")
    fun observeForRace(season: Int, round: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE season = :season AND round = :round")
    suspend fun getForRace(season: Int, round: Int): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE season = :season AND round = :round")
    suspend fun deleteForRace(season: Int, round: Int)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface CacheDao {

    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    suspend fun get(key: String): CacheMetaEntity?

    @Upsert
    suspend fun put(meta: CacheMetaEntity)

    @Query("DELETE FROM cache_meta")
    suspend fun clear()
}
