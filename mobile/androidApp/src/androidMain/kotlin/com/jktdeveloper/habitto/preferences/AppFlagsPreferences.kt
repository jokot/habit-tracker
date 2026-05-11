package com.jktdeveloper.habitto.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appFlagsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_flags",
)

class AppFlagsPreferences(private val context: Context) {

    private object Keys {
        val SEEN_RATE_LADDER_BANNER = booleanPreferencesKey("seen_rate_ladder_upgrade_banner")
    }

    val seenRateLadderUpgradeBanner: Flow<Boolean> = context.appFlagsDataStore.data
        .map { it[Keys.SEEN_RATE_LADDER_BANNER] ?: false }

    suspend fun current(): Boolean = seenRateLadderUpgradeBanner.first()

    suspend fun setSeenRateLadderUpgradeBanner(seen: Boolean) {
        context.appFlagsDataStore.edit { it[Keys.SEEN_RATE_LADDER_BANNER] = seen }
    }
}
