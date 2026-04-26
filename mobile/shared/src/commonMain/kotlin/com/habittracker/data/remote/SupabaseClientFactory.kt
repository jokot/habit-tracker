package com.habittracker.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientFactory {
    fun create(url: String, key: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth) {
                // Defaults are already true in supabase-kt 3.x but pin them
                // explicitly so a future SDK upgrade can't silently disable
                // session refresh and break long-lived sync workers.
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
        }
}
