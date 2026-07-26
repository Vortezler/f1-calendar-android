package com.praval.f1calendar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store get() = context.settingsDataStore

    /** When true the whole app renders times in UTC instead of the device timezone. */
    val useUtc: Flow<Boolean> = store.data.map { it[KEY_USE_UTC] ?: false }

    /** Master switch; individual sessions still need their own reminder toggled on. */
    val remindersEnabled: Flow<Boolean> = store.data.map { it[KEY_REMINDERS_ENABLED] ?: true }

    /** [FOLLOW_CURRENT] means "whatever season the API says is live". */
    val selectedSeason: Flow<Int> = store.data.map { it[KEY_SELECTED_SEASON] ?: FOLLOW_CURRENT }

    /**
     * The season the API most recently reported as current. Cached so the season picker and the
     * calendar have a sane default before the first network call returns.
     */
    val resolvedCurrentSeason: Flow<Int> = store.data.map { it[KEY_RESOLVED_SEASON] ?: 0 }

    suspend fun setUseUtc(value: Boolean) = store.edit { it[KEY_USE_UTC] = value }

    suspend fun setRemindersEnabled(value: Boolean) = store.edit { it[KEY_REMINDERS_ENABLED] = value }

    suspend fun setSelectedSeason(season: Int) = store.edit { it[KEY_SELECTED_SEASON] = season }

    suspend fun setResolvedCurrentSeason(season: Int) = store.edit { it[KEY_RESOLVED_SEASON] = season }

    suspend fun remindersEnabledNow(): Boolean = remindersEnabled.first()

    suspend fun resolvedCurrentSeasonNow(): Int = resolvedCurrentSeason.first()

    companion object {
        const val FOLLOW_CURRENT = 0

        private val KEY_USE_UTC = booleanPreferencesKey("use_utc")
        private val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        private val KEY_SELECTED_SEASON = intPreferencesKey("selected_season")
        private val KEY_RESOLVED_SEASON = intPreferencesKey("resolved_current_season")
    }
}
