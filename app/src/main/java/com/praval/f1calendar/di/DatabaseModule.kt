package com.praval.f1calendar.di

import android.content.Context
import androidx.room.Room
import com.praval.f1calendar.data.local.F1Database
import com.praval.f1calendar.data.local.dao.CacheDao
import com.praval.f1calendar.data.local.dao.RaceDao
import com.praval.f1calendar.data.local.dao.ReminderDao
import com.praval.f1calendar.data.local.dao.RecordsDao
import com.praval.f1calendar.data.local.dao.ResultDao
import com.praval.f1calendar.data.local.dao.SessionRuleDao
import com.praval.f1calendar.data.local.dao.StandingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): F1Database =
        Room.databaseBuilder(context, F1Database::class.java, F1Database.NAME)
            .addMigrations(F1Database.MIGRATION_2_3)
            // Backstop only. Real migrations are written for anything that would otherwise discard
            // the user's alarm rules; this catches upgrades from schemas that predate them.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRaceDao(db: F1Database): RaceDao = db.raceDao()

    @Provides
    fun provideResultDao(db: F1Database): ResultDao = db.resultDao()

    @Provides
    fun provideStandingsDao(db: F1Database): StandingsDao = db.standingsDao()

    @Provides
    fun provideReminderDao(db: F1Database): ReminderDao = db.reminderDao()

    @Provides
    fun provideSessionRuleDao(db: F1Database): SessionRuleDao = db.sessionRuleDao()

    @Provides
    fun provideRecordsDao(db: F1Database): RecordsDao = db.recordsDao()

    @Provides
    fun provideCacheDao(db: F1Database): CacheDao = db.cacheDao()
}
