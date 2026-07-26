package com.praval.f1calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.RaceDao
import com.praval.f1calendar.data.local.dao.ReminderDao
import com.praval.f1calendar.data.local.dao.ResultDao
import com.praval.f1calendar.data.local.dao.StandingsDao
import com.praval.f1calendar.data.local.entity.CacheMetaEntity
import com.praval.f1calendar.data.local.entity.ConstructorStandingEntity
import com.praval.f1calendar.data.local.entity.DriverStandingEntity
import com.praval.f1calendar.data.local.entity.QualifyingResultEntity
import com.praval.f1calendar.data.local.entity.RaceEntity
import com.praval.f1calendar.data.local.entity.RaceResultEntity
import com.praval.f1calendar.data.local.entity.ReminderEntity

@Database(
    entities = [
        RaceEntity::class,
        RaceResultEntity::class,
        QualifyingResultEntity::class,
        DriverStandingEntity::class,
        ConstructorStandingEntity::class,
        ReminderEntity::class,
        CacheMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class F1Database : RoomDatabase() {
    abstract fun raceDao(): RaceDao
    abstract fun resultDao(): ResultDao
    abstract fun standingsDao(): StandingsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val NAME = "f1-calendar.db"
    }
}
