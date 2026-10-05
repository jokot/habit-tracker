package com.jktdeveloper.habitto

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tells a session that the server ended apart from a sign-out of the app. When the server
 * rejects the refresh token, supabase-kt deletes the session and reports no session. The
 * app still remembers the user, so without this check it shows the data of that user as
 * signed out, and nothing ends the session (#33).
 */
class ServerSessionEnd(private val rememberedUserId: () -> String?) {
    private val ownSignOuts = AtomicInteger(0)

    /** Runs a sign-out of the app. No session during [block] is not a server end. */
    suspend fun <T> ownSignOut(block: suspend () -> T): T {
        ownSignOuts.incrementAndGet()
        try {
            return block()
        } finally {
            ownSignOuts.decrementAndGet()
        }
    }

    /** Call when the auth client reports no session. A guest has no session to end. */
    fun endedByServer(): Boolean = ownSignOuts.get() == 0 && rememberedUserId() != null
}
