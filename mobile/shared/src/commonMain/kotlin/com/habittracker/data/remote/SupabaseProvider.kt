package com.habittracker.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {
    // ⚠️  NEVER commit real credentials here.
    // Copy local.properties.example → local.properties and set:
    //   supabase.url=https://YOUR_PROJECT_REF.supabase.co
    //   supabase.anon_key=YOUR_ANON_KEY
    // Phase 2 will inject these via BuildConfig (Android) and xcconfig (iOS).
    private const val SUPABASE_URL = "https://YOUR_PROJECT_REF.supabase.co"
    private const val SUPABASE_ANON_KEY = "YOUR_ANON_KEY"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }
}
