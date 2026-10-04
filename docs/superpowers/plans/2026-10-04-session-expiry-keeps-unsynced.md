# Session Expiry Keeps Unsynced Changes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A session expiry keeps the unsynced changes of the user on the phone, and the next sign-in with the same account pushes them (issue #33).

**Architecture:** A new `HeldAccountStore` (SharedPreferences) records one held account. A new plain class `HeldAccounts` holds the keep-or-delete decisions, with the count and the row delete passed in as functions, so a Robolectric test can drive it. `AppContainer` calls it from `handleSessionExpired` and from `migrateLocalToAuthenticated`. The toast and a notice card on the Auth screen tell the user which account to use.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), SQLDelight, SharedPreferences, JUnit 4, Robolectric, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-04-session-expiry-keeps-unsynced-design.md`

## Global Constraints

- Worktree: `/Users/jokot/dev/habit-tracker/.worktrees/fix-session-expiry-push`, branch `fix/session-expiry-push`. Run `./gradlew` from the worktree root.
- Prefix shell commands with `rtk`.
- No change to `SyncEngine`, the push, the pull, the Today sign-out or the Settings sign-out.
- Unsynced change = a row with `syncedAt = null` in any of the six tables: habits, want_activities, habit_logs, want_logs, user_identities, habit_identities.
- User-facing copy is exactly the text in spec §4.4. Copy lives in pure functions in `ui/auth/SessionExpiryCopy.kt`.
- Comments, commits and the PR use STE rules: active voice, no contractions, no semicolons, at most 25 words in a sentence.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A different account signs in.** The rows of the held user are deleted before the first sync of the new account. Pinned by `HeldAccountsTest` case 5 and by the order in `migrateLocalToAuthenticated` (Task 3).
2. **The guest writes after the expiry.** Guest rows use the guest id, so the held-account code never moves or deletes them. Pinned by `HeldAccountsTest` case 5, which checks that only the held user id is deleted.
3. **The count fails.** The rows stay and the account is held. Pinned by `HeldAccountsTest` case 3.
4. **A process restart between the expiry and the sign-in.** The hold is in SharedPreferences, and the Auth screen counts again on open. Pinned by `HeldAccountStoreTest` (a new store instance reads the hold).
5. **The email is null.** The notice uses the body with no email, and the email field stays empty. Pinned by `SessionExpiryCopyTest` and `AuthUiStateHeldAccountTest`.

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HeldAccountStore.kt` | create | Store one held account in SharedPreferences. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HeldAccounts.kt` | create | The keep-or-delete decisions of spec §4.2 and §4.3. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopy.kt` | create | Toast and notice text. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | modify | Wire `HeldAccounts`, split the row delete, change the event type. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | modify | Toast text from the event. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthViewModel.kt` | modify | Load the held account summary, fill the email field. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthScreen.kt` | modify | Show the notice card. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/HeldAccountStoreTest.kt` | create | Store tests. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/HeldAccountsTest.kt` | create | Decision tests. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopyTest.kt` | create | Copy tests. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/AuthUiStateHeldAccountTest.kt` | create | Email-fill tests. |
| `docs/sync-flow.md`, `docs/BACKLOG.md` | modify | Record the new behaviour. |

---

### Task 1: HeldAccountStore and HeldAccounts

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HeldAccountStore.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HeldAccounts.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/HeldAccountStoreTest.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/HeldAccountsTest.kt`

**Interfaces:**
- Produces:
  - `data class HeldAccount(val userId: String, val email: String?)`
  - `class HeldAccountStore(context: Context)` with `fun get(): HeldAccount?`, `fun hold(account: HeldAccount)`, `fun clear()`
  - `data class HeldAccountSummary(val email: String?, val unsyncedCount: Int?)`
  - `class HeldAccounts(store: HeldAccountStore, countUnsynced: suspend (String) -> Int, deleteUserRows: suspend (String) -> Unit)` with:
    - `suspend fun onSessionExpired(userId: String, email: String?): Int?` — returns the unsynced count, `0` when the rows are deleted, `null` when the count failed.
    - `suspend fun onSignedIn(userId: String)`
    - `suspend fun summary(): HeldAccountSummary?`

- [ ] **Step 1: Write the failing store test**

```kotlin
package com.jktdeveloper.habitto

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class HeldAccountStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = HeldAccountStore(context)

    @Before fun clean() = store.clear()

    @Test fun `empty store holds no account`() {
        assertNull(store.get())
    }

    @Test fun `a new store instance reads the held account`() {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        assertEquals(HeldAccount("auth-1", "a@b.com"), HeldAccountStore(context).get())
    }

    @Test fun `a held account can have no email`() {
        store.hold(HeldAccount("auth-1", null))
        assertEquals(HeldAccount("auth-1", null), store.get())
    }

    @Test fun `clear removes the held account`() {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        store.clear()
        assertNull(store.get())
    }
}
```

- [ ] **Step 2: Write the failing decision test**

```kotlin
package com.jktdeveloper.habitto

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class HeldAccountsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = HeldAccountStore(context)
    private val deleted = mutableListOf<String>()
    private var unsynced: Int = 0
    private var countFails = false
    private var deleteFails = false

    private val held = HeldAccounts(
        store = store,
        countUnsynced = { if (countFails) error("db") else unsynced },
        deleteUserRows = { userId -> if (deleteFails) error("db") else deleted += userId },
    )

    @Before fun clean() = store.clear()

    @Test fun `expiry with no unsynced changes deletes the rows and holds nothing`() = runTest {
        unsynced = 0
        assertEquals(0, held.onSessionExpired("auth-1", "a@b.com"))
        assertEquals(listOf("auth-1"), deleted)
        assertNull(store.get())
    }

    @Test fun `expiry with unsynced changes keeps the rows and holds the user`() = runTest {
        unsynced = 3
        assertEquals(3, held.onSessionExpired("auth-1", "a@b.com"))
        assertEquals(emptyList<String>(), deleted)
        assertEquals(HeldAccount("auth-1", "a@b.com"), store.get())
    }

    @Test fun `expiry where the count fails keeps the rows and holds the user`() = runTest {
        countFails = true
        assertNull(held.onSessionExpired("auth-1", null))
        assertEquals(emptyList<String>(), deleted)
        assertEquals(HeldAccount("auth-1", null), store.get())
    }

    @Test fun `expiry where the delete fails still returns 0`() = runTest {
        unsynced = 0
        deleteFails = true
        assertEquals(0, held.onSessionExpired("auth-1", "a@b.com"))
        assertNull(store.get())
    }

    @Test fun `sign-in as the held user keeps the rows and clears the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        held.onSignedIn("auth-1")
        assertEquals(emptyList<String>(), deleted)
        assertNull(store.get())
    }

    @Test fun `sign-in as another user deletes only the held rows and clears the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        held.onSignedIn("auth-2")
        assertEquals(listOf("auth-1"), deleted)
        assertNull(store.get())
    }

    @Test fun `sign-in where the delete fails keeps the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        deleteFails = true
        held.onSignedIn("auth-2")
        assertEquals(HeldAccount("auth-1", "a@b.com"), store.get())
    }

    @Test fun `sign-in with no hold deletes nothing`() = runTest {
        held.onSignedIn("auth-2")
        assertEquals(emptyList<String>(), deleted)
    }

    @Test fun `summary gives the held email and the current count`() = runTest {
        assertNull(held.summary())
        store.hold(HeldAccount("auth-1", "a@b.com"))
        unsynced = 5
        assertEquals(HeldAccountSummary("a@b.com", 5), held.summary())
        countFails = true
        assertEquals(HeldAccountSummary("a@b.com", null), held.summary())
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*HeldAccount*'`
Expected: FAIL, compile errors for `HeldAccountStore`, `HeldAccount`, `HeldAccounts`, `HeldAccountSummary`.

- [ ] **Step 4: Write HeldAccountStore**

```kotlin
package com.jktdeveloper.habitto

import android.content.Context

/** A user whose session expired while this phone had unsynced changes for that user. */
data class HeldAccount(val userId: String, val email: String?)

/**
 * Remembers the one held account across process death. A sign-in always resolves the
 * hold, so the store never needs more than one. See [HeldAccounts].
 */
class HeldAccountStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): HeldAccount? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return HeldAccount(userId, prefs.getString(KEY_EMAIL, null))
    }

    fun hold(account: HeldAccount) {
        prefs.edit()
            .putString(KEY_USER_ID, account.userId)
            .putString(KEY_EMAIL, account.email)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "held_account"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
```

- [ ] **Step 5: Write HeldAccounts**

```kotlin
package com.jktdeveloper.habitto

import android.util.Log

/** What the Auth screen shows about a held account. A null count means the count failed. */
data class HeldAccountSummary(val email: String?, val unsyncedCount: Int?)

/**
 * Decides what happens to the local rows of a user whose session expired. The token
 * refresh failed, so no push is possible. Unsynced changes stay on the phone until the
 * next sign-in (#33). See docs/superpowers/specs/2026-10-04-session-expiry-keeps-unsynced-design.md.
 */
class HeldAccounts(
    private val store: HeldAccountStore,
    private val countUnsynced: suspend (userId: String) -> Int,
    private val deleteUserRows: suspend (userId: String) -> Unit,
) {
    /**
     * Returns the unsynced count of [userId]. With 0 the rows are deleted. Otherwise the
     * rows stay and the user is held. Null means the count failed, and the rows stay.
     */
    suspend fun onSessionExpired(userId: String, email: String?): Int? {
        val count = runCatching { countUnsynced(userId) }
            .onFailure { e -> Log.w(TAG, "Could not count unsynced changes", e) }
            .getOrNull()
        if (count == 0) {
            runCatching { deleteUserRows(userId) }
                .onFailure { e -> Log.w(TAG, "Could not delete the rows of an expired session", e) }
            return 0
        }
        store.hold(HeldAccount(userId, email))
        return count
    }

    /**
     * Resolves the hold at sign-in. The same user keeps the rows, and the next push sends
     * them. Another user cannot push them, so they are deleted.
     */
    suspend fun onSignedIn(userId: String) {
        val held = store.get() ?: return
        if (held.userId != userId) {
            val deleted = runCatching { deleteUserRows(held.userId) }
                .onFailure { e -> Log.w(TAG, "Could not delete the rows of a held account", e) }
                .isSuccess
            // Keep the hold, so the next sign-in tries the delete again.
            if (!deleted) return
        }
        store.clear()
    }

    suspend fun summary(): HeldAccountSummary? {
        val held = store.get() ?: return null
        return HeldAccountSummary(held.email, runCatching { countUnsynced(held.userId) }.getOrNull())
    }

    private companion object {
        const val TAG = "HeldAccounts"
    }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*HeldAccount*'`
Expected: PASS, 13 tests.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HeldAccount*.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/HeldAccount*.kt
rtk git commit -m "feat(auth): hold an expired account that has unsynced changes

Refs #33.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Toast and notice copy

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopy.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopyTest.kt`

**Interfaces:**
- Produces: `fun sessionExpiredToast(unsyncedCount: Int?): String`, `fun heldNoticeTitle(unsyncedCount: Int?): String`, `fun heldNoticeBody(email: String?): String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jktdeveloper.habitto.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExpiryCopyTest {
    @Test fun `toast text follows the unsynced count`() {
        assertEquals("Session expired. Sign in again.", sessionExpiredToast(0))
        assertEquals("Session expired. Sign in again to save 1 change.", sessionExpiredToast(1))
        assertEquals("Session expired. Sign in again to save 3 changes.", sessionExpiredToast(3))
        assertEquals("Session expired. Sign in again to save your changes.", sessionExpiredToast(null))
    }

    @Test fun `notice title follows the unsynced count`() {
        assertEquals("1 change not synced", heldNoticeTitle(1))
        assertEquals("3 changes not synced", heldNoticeTitle(3))
        assertEquals("Changes not synced", heldNoticeTitle(null))
    }

    @Test fun `notice body names the account when the email is known`() {
        assertEquals(
            "Sign in as a@b.com to save them. If you use another account, they are deleted from this phone.",
            heldNoticeBody("a@b.com"),
        )
        assertEquals(
            "Sign in with the account you used before to save them. If you use another account, they are deleted from this phone.",
            heldNoticeBody(null),
        )
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*SessionExpiryCopyTest*'`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Write the copy**

```kotlin
package com.jktdeveloper.habitto.ui.auth

// Text for a session expiry that kept unsynced changes. A null count means the count failed.

fun sessionExpiredToast(unsyncedCount: Int?): String = when (unsyncedCount) {
    0 -> "Session expired. Sign in again."
    null -> "Session expired. Sign in again to save your changes."
    1 -> "Session expired. Sign in again to save 1 change."
    else -> "Session expired. Sign in again to save $unsyncedCount changes."
}

fun heldNoticeTitle(unsyncedCount: Int?): String = when (unsyncedCount) {
    null -> "Changes not synced"
    1 -> "1 change not synced"
    else -> "$unsyncedCount changes not synced"
}

fun heldNoticeBody(email: String?): String {
    val who = if (email != null) "Sign in as $email" else "Sign in with the account you used before"
    return "$who to save them. If you use another account, they are deleted from this phone."
}
```

- [ ] **Step 4: Run the test to see it pass**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*SessionExpiryCopyTest*'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopy.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/SessionExpiryCopyTest.kt
rtk git commit -m "feat(auth): add the text for a session expiry with unsynced changes

Refs #33.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Wire HeldAccounts into AppContainer and the toast

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` (fields near line 108, `sessionExpiredEvents` at 266, `migrateLocalToAuthenticated` at 335, `clearAuthenticatedUserData` at 384, `handleSessionExpired` at 413)
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt:139-145`

**Interfaces:**
- Consumes: `HeldAccounts`, `HeldAccountStore`, `HeldAccountSummary` (Task 1), `sessionExpiredToast` (Task 2)
- Produces:
  - `data class SessionExpired(val unsyncedCount: Int?)` in `AppContainer.kt`
  - `val sessionExpiredEvents: SharedFlow<SessionExpired>`
  - `suspend fun AppContainer.heldAccountSummary(): HeldAccountSummary?`

`AppContainer` builds a Supabase client, so no unit test constructs it. The decisions are tested in Task 1. This task is wiring only, and the build plus the device check in Task 5 verify it.

- [ ] **Step 1: Add the fields** after `val wantLogRepository = LocalWantLogRepository(db)`:

```kotlin
    private val heldAccounts = HeldAccounts(
        store = HeldAccountStore(context),
        countUnsynced = { userId -> countUnsyncedChanges(userId) },
        deleteUserRows = { userId -> deleteUserRows(userId) },
    )
```

- [ ] **Step 2: Change the event type.** Replace lines 266-267:

```kotlin
    private val _sessionExpiredEvents = MutableSharedFlow<SessionExpired>(extraBufferCapacity = 1)
    val sessionExpiredEvents: SharedFlow<SessionExpired> = _sessionExpiredEvents.asSharedFlow()
```

Add at the end of the file, after the class:

```kotlin
/** The session ended with no push. [unsyncedCount] is the number of changes kept, null if unknown. */
data class SessionExpired(val unsyncedCount: Int?)
```

- [ ] **Step 3: Resolve the hold at sign-in.** In `migrateLocalToAuthenticated`, insert as the first line of the body, before `val localId = ...`:

```kotlin
        // Before the guest migration and the first sync: rows of another held account
        // must go before this account pulls. See HeldAccounts.
        heldAccounts.onSignedIn(authUserId)
```

- [ ] **Step 4: Split the row delete.** Replace `clearAuthenticatedUserData` (lines 384-401) with:

```kotlin
    suspend fun clearAuthenticatedUserData(authUserId: String) {
        deleteUserRows(authUserId)
        forgetSyncedUser()
    }

    private fun deleteUserRows(userId: String) {
        db.habitTrackerDatabaseQueries.transaction {
            // Identity tables first — habit_identities subquery references LocalHabit.userId,
            // so it must run before LocalHabit rows are deleted.
            db.habitTrackerDatabaseQueries.clearHabitIdentitiesForUser(userId)
            db.habitTrackerDatabaseQueries.deleteAllUserIdentitiesForUser(userId)
            db.habitTrackerDatabaseQueries.clearHabitsForUser(userId)
            db.habitTrackerDatabaseQueries.clearHabitLogsForUser(userId)
            db.habitTrackerDatabaseQueries.clearWantLogsForUser(userId)
            db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(userId)
        }
    }

    private fun forgetSyncedUser() {
        // Reset pull watermarks so the next sign-in pulls everything from
        // the cloud instead of skipping rows older than the cached watermark.
        watermarks.reset()
        // We just deleted this user's local rows, so stop claiming to be them on the
        // next cold start. Covers every sign-out path — they all come through here.
        lastAuthUserStore.clear()
    }

    /** Rows with `syncedAt = null` across the six synced tables. */
    private suspend fun countUnsyncedChanges(userId: String): Int =
        habitRepository.getUnsyncedFor(userId).size +
            wantActivityRepository.getUnsyncedFor(userId).size +
            habitLogRepository.getUnsyncedFor(userId).size +
            wantLogRepository.getUnsyncedFor(userId).size +
            identityRepository.getUnsyncedUserIdentitiesFor(userId).size +
            identityRepository.getUnsyncedHabitIdentitiesFor(userId).size

    suspend fun heldAccountSummary(): HeldAccountSummary? = heldAccounts.summary()
```

- [ ] **Step 5: Keep the changes at expiry.** Replace the lines after the refresh check in `handleSessionExpired` (lines 420-424) with:

```kotlin
        // The refresh failed, so no push is possible. HeldAccounts keeps unsynced changes
        // for the next sign-in instead of deleting them (#33).
        val unsynced = heldAccounts.onSessionExpired(currentUserId(), authRepository.currentEmail())
        forgetSyncedUser()
        runCatching { authRepository.signOut() }
        refreshAuthState()
        _sessionExpiredEvents.tryEmit(SessionExpired(unsynced))
```

- [ ] **Step 6: Update the toast** in `AppNavigation.kt`. Replace the `Toast.makeText(...)` line inside `sessionExpiredEvents.collect`:

```kotlin
        container.sessionExpiredEvents.collect { event ->
            Toast.makeText(context, sessionExpiredToast(event.unsyncedCount), Toast.LENGTH_LONG).show()
```

Add `import com.jktdeveloper.habitto.ui.auth.sessionExpiredToast`.

- [ ] **Step 7: Build and run all unit tests**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin :mobile:androidApp:testDebugUnitTest :mobile:shared:jvmTest`
Expected: BUILD SUCCESSFUL. All tests pass.

- [ ] **Step 8: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "fix(auth): keep unsynced changes when the session expires

A failed token refresh deleted every local row of the user. Changes that
no push had sent were lost. The app now keeps the rows of a user with
unsynced changes. The next sign-in with that account pushes them. A
sign-in with another account deletes them.

Fixes #33.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Auth screen notice

Implement this task after the Habitto design for the notice is approved. Match the approved design for colour, icon and spacing. The code below is the default if the design keeps the existing "Link sent" card style.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthScreen.kt` (body block at line 177)
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/AuthUiStateHeldAccountTest.kt`

**Interfaces:**
- Consumes: `AppContainer.heldAccountSummary()` (Task 3), `heldNoticeTitle`, `heldNoticeBody` (Task 2), `HeldAccountSummary` (Task 1)
- Produces: `AuthUiState.heldAccount: HeldAccountSummary?`, `internal fun AuthUiState.withHeldAccount(held: HeldAccountSummary?): AuthUiState`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jktdeveloper.habitto.ui.auth

import com.jktdeveloper.habitto.HeldAccountSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthUiStateHeldAccountTest {
    @Test fun `held email fills an empty email field`() {
        val state = AuthUiState().withHeldAccount(HeldAccountSummary("a@b.com", 3))
        assertEquals("a@b.com", state.email)
        assertEquals(HeldAccountSummary("a@b.com", 3), state.heldAccount)
    }

    @Test fun `held email does not replace a typed email`() {
        val state = AuthUiState(email = "c@d.com").withHeldAccount(HeldAccountSummary("a@b.com", 3))
        assertEquals("c@d.com", state.email)
    }

    @Test fun `no email leaves the field empty`() {
        assertEquals("", AuthUiState().withHeldAccount(HeldAccountSummary(null, 3)).email)
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*AuthUiStateHeldAccountTest*'`
Expected: FAIL, unresolved `withHeldAccount` and `heldAccount`.

- [ ] **Step 3: Change AuthViewModel**

Add the field as the last parameter of `AuthUiState`:

```kotlin
    val heldAccount: HeldAccountSummary? = null,
```

Add after the `AuthUiState` class:

```kotlin
/** Shows the held account, and fills the email field with its email if the field is empty. */
internal fun AuthUiState.withHeldAccount(held: HeldAccountSummary?): AuthUiState = copy(
    heldAccount = held,
    email = if (email.isEmpty() && held?.email != null) held.email else email,
)
```

Add an `init` block to `AuthViewModel`, after the `events` property:

```kotlin
    init {
        // Count again on each open, so the number is correct after a process restart.
        viewModelScope.launch {
            _uiState.value = _uiState.value.withHeldAccount(container.heldAccountSummary())
        }
    }
```

Add `import com.jktdeveloper.habitto.HeldAccountSummary`.

- [ ] **Step 4: Run the test to see it pass**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests '*AuthUiStateHeldAccountTest*'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Add the notice card to AuthScreen**

Add at the end of `AuthScreen.kt`:

```kotlin
@Composable
private fun HeldAccountNotice(held: HeldAccountSummary) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = heldNoticeTitle(held.unsyncedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = heldNoticeBody(held.email),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
```

In the body block, change `if (checkEmail != null) {` so the `else` branch (the form) starts with the notice:

```kotlin
            } else {
                uiState.heldAccount?.let { held ->
                    HeldAccountNotice(held)
                    Spacer(Modifier.height(Spacing.xl))
                }
                // ... existing form content
```

Add imports: `androidx.compose.material.icons.outlined.CloudOff`, `com.jktdeveloper.habitto.HeldAccountSummary`.

- [ ] **Step 6: Build and run all unit tests**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug :mobile:androidApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. All tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/ mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/auth/AuthUiStateHeldAccountTest.kt
rtk git commit -m "feat(auth): show held unsynced changes on the Auth screen

Refs #33.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Device check, docs and PR

**Files:**
- Modify: `docs/sync-flow.md` §6 (sign in, sign out, session expiry) and §7 (known risks)
- Modify: `docs/BACKLOG.md` (Known bugs item 1)

- [ ] **Step 1: Point the debug build at local Supabase and install it**

Start local Supabase with `supabase start`. Put the local API URL and anon key in `local.properties`. Do not print the keys. Then run `rtk ./gradlew :mobile:androidApp:installDebug` in the background, and ask the user to tap "Continue installation" on the phone.

- [ ] **Step 2: Same-account check (spec §6, steps 1 to 5)**

1. Sign in. Turn on airplane mode. Log 3 habits.
2. In local Studio SQL, run `delete from auth.refresh_tokens where user_id = '<id>'; delete from auth.sessions where user_id = '<id>';`
3. Turn off airplane mode. Pull to refresh on Today.
4. Expect the toast `Session expired. Sign in again to save 3 changes.` Expect the Auth notice `3 changes not synced` with the email in the field.
5. Sign in. Expect the 3 logs on Today, and 3 rows in `public.habit_logs` for the user.

- [ ] **Step 3: Different-account check (spec §6, step 6)**

Do steps 1 to 4 again, then sign in with a second account. Expect none of the 3 logs on Today. Expect `adb shell run-as com.jktdeveloper.habitto sqlite3 databases/<db> "select count(*) from HabitLog where userId = '<first id>'"` to print 0.

- [ ] **Step 4: Update the docs**

- `docs/sync-flow.md` §6: change the session-expiry diagram and text. The app keeps the rows when the unsynced count is more than 0, and the next sign-in resolves the hold.
- `docs/sync-flow.md` §7: remove the session-expiry risk.
- `docs/BACKLOG.md`: mark Known bugs item 1 as fixed by this PR.

- [ ] **Step 5: Commit, push and open the PR**

```bash
rtk git add docs/sync-flow.md docs/BACKLOG.md
rtk git commit -m "docs(sync): record that a session expiry keeps unsynced changes

Refs #33.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
rtk git -c credential.helper= -c 'credential.helper=!f() { echo username=jokot; echo "password=$(gh auth token --user jokot)"; }; f' push -u origin fix/session-expiry-push
GH_TOKEN=$(gh auth token --user jokot) gh pr create --base main --title "fix(auth): keep unsynced changes when the session expires" --body-file <body>
```

The PR body lists the change, the tests, the device check results and `Fixes #33`. It ends with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
