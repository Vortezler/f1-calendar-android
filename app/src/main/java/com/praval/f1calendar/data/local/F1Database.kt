package com.praval.f1calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.praval.f1calendar.data.local.dao.RecordsDao
import com.praval.f1calendar.data.local.entity.CircuitEntity
import com.praval.f1calendar.data.local.entity.LapRecordEntity
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.RaceDao
import com.praval.f1calendar.data.local.dao.ReminderDao
import com.praval.f1calendar.data.local.dao.ResultDao
import com.praval.f1calendar.data.local.dao.ResultRuleDao
import com.praval.f1calendar.data.local.dao.SessionRuleDao
import com.praval.f1calendar.data.local.dao.StandingsDao
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.local.entity.ConstructorStandingEntity
import com.praval.f1calendar.data.local.entity.DriverStandingEntity
import com.praval.f1calendar.data.local.entity.QualifyingResultEntity
import com.praval.f1calendar.data.local.entity.RaceEntity
import com.praval.f1calendar.data.local.entity.RaceResultEntity
import com.praval.f1calendar.data.local.entity.ReminderEntity
import com.praval.f1calendar.data.local.entity.ResultRuleEntity
import com.praval.f1calendar.data.local.entity.SessionRuleEntity

@Database(
    entities = [
        RaceEntity::class,
        RaceResultEntity::class,
        QualifyingResultEntity::class,
        DriverStandingEntity::class,
        ConstructorStandingEntity::class,
        ReminderEntity::class,
        SessionRuleEntity::class,
        CircuitEntity::class,
        LapRecordEntity::class,
        ResultRuleEntity::class,
        CacheMetaEntity::class,
    ],
    // v2 added session_rules and ReminderEntity.enabled.
    // v3 added circuits and lap_records.
    // v4 adds result_rules. All migrated properly rather than destructively so alarm and
    // notification preferences survive the upgrade.
    version = 4,
    exportSchema = true,
)
abstract class F1Database : RoomDatabase() {
    abstract fun raceDao(): RaceDao
    abstract fun resultDao(): ResultDao
    abstract fun standingsDao(): StandingsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun sessionRuleDao(): SessionRuleDao
    abstract fun recordsDao(): RecordsDao
    abstract fun resultRuleDao(): ResultRuleDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val NAME = "f1-calendar.db"

        /**
         * Purely additive: two new tables, nothing existing touched. Written by hand rather than
         * falling back to a destructive migration so that alarm rules and per-weekend overrides
         * survive the upgrade.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `circuits` (" +
                        "`circuitId` TEXT NOT NULL, " +
                        "`circuitName` TEXT NOT NULL, " +
                        "`locality` TEXT, " +
                        "`country` TEXT, " +
                        "`lat` REAL, " +
                        "`lng` REAL, " +
                        "`wikiUrl` TEXT, " +
                        "PRIMARY KEY(`circuitId`))",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lap_records` (" +
                        "`circuitId` TEXT NOT NULL, " +
                        "`timeText` TEXT NOT NULL, " +
                        "`millis` INTEGER NOT NULL, " +
                        "`driverId` TEXT NOT NULL, " +
                        "`givenName` TEXT NOT NULL, " +
                        "`familyName` TEXT NOT NULL, " +
                        "`driverCode` TEXT, " +
                        "`constructorId` TEXT NOT NULL, " +
                        "`constructorName` TEXT NOT NULL, " +
                        "`season` INTEGER NOT NULL, " +
                        "`raceName` TEXT NOT NULL, " +
                        "PRIMARY KEY(`circuitId`))",
                )
            }
        }

        /** Purely additive: one new table, nothing existing touched. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `result_rules` (" +
                        "`session` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`session`))",
                )
            }
        }
    }
}
