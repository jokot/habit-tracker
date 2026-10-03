# Session expiry keeps unsynced changes: design

**Date:** 2026-10-04
**Branch:** `fix/session-expiry-push`
**Refs:** issue #33, `docs/BACKLOG.md` Known bugs item 1, `docs/sync-flow.md` §6

## 1. Problem

The sync gets a "Session expired" error, and the token refresh fails. Then `AppContainer.handleSessionExpired` deletes all local rows of the user and signs out. It does not check for unsynced rows first.

A change made offline has `syncedAt = null` until a push sends it. If the session expires before that push, the change is lost. It is not on the phone and it is not on the server.

The fix cannot push before the delete. `handleSessionExpired` runs only after the refresh failed, so the app has no valid token for a push.

## 2. Goal

1. A session expiry never deletes an unsynced change.
2. When the same account signs in again, the next sync pushes the kept changes.
3. The user knows which account to sign in with, and how many changes wait.
4. A sign-in with a different account deletes the kept changes. The Auth screen tells the user this before the sign-in.

## 3. Non-goals

- The sign-out from Today and the sign-out from Settings do not change. The user starts these, and the Today dialog already shows the unsynced count.
- No change to `SyncEngine`, the push or the pull.
- No change to the "Session expired" system notification.
- More than one held account. A sign-in always resolves the held account (§4.3), so there is never more than one.

## 4. Design

### 4.1 Terms

- **Unsynced change:** a local row of the user with `syncedAt = null`, in any of the six synced tables: habits, want_activities, habit_logs, want_logs, user_identities, habit_identities.
- **Held account:** a user whose session expired while the phone had unsynced changes for that user. The phone keeps all rows of that user until the next sign-in.

### 4.2 Session expiry

`handleSessionExpired` keeps its first step: it tries one token refresh. If the refresh succeeds, nothing changes from today.

If the refresh fails:

1. The app counts the unsynced changes of the user, across all six tables.
2. **Count is 0:** the app deletes the rows of the user, as today.
3. **Count is more than 0:** the app keeps every row of the user. It writes a held account to `HeldAccountStore`, with the user id and the email. The email can be null, because the expired session can have no user.
4. In both cases the app resets the pull watermarks, clears `LastAuthUserStore`, signs out and publishes the new auth state. Then it emits a session-expired event with the count.

The kept rows stay on the phone, but no screen shows them:

- Every local query filters by `userId`. After the sign-out the app uses the guest id, so the guest sees none of the kept rows.
- The widgets read `LastAuthUserStore`. It is clear, so the widgets also use the guest id.
- The push reads only the rows of the signed-in user. Another account never sends the kept rows.

### 4.3 Next sign-in

`AuthViewModel.completeSignIn` calls `migrateLocalToAuthenticated` first. The held account check runs at the start of that call, before the guest migration and before the first sync.

- **No held account:** nothing changes from today.
- **Same account** (`held.userId == session.userId`): the app keeps the rows and clears the held account. The push runs before the pull, so the first sync sends the kept changes. Then the pull fetches all tables from watermark 0.
- **Different account:** the app deletes all rows of the held user and clears the held account. The new account cannot push those rows, because RLS rejects a row with another `user_id`.

The guest migration then runs as today. It moves or deletes only the rows of the guest id, so it never touches the kept rows.

### 4.4 What the user sees

**Toast after the expiry** (`AppNavigation`, on the session-expired event):

| Count | Text |
|---|---|
| 0 | `Session expired. Sign in again.` |
| 1 | `Session expired. Sign in again to save 1 change.` |
| N | `Session expired. Sign in again to save N changes.` |
| unknown (the count failed) | `Session expired. Sign in again to save your changes.` |

**Notice on the Auth screen.** While a held account exists, the Auth screen shows a notice card under the subtitle, above the form. It shows in both the sign-in mode and the sign-up mode.

- Title: `3 changes not synced` (`1 change not synced` for one, `Changes not synced` if the count fails).
- Body with an email: `Sign in as a@b.com to save them. If you use another account, they are deleted from this phone.`
- Body with no email: `Sign in with the account you used before to save them. If you use another account, they are deleted from this phone.`
- If the email is known and the email field is empty, the app writes the email into the field.

The Auth screen reads the count again each time it opens, so the number is correct after a process restart.

### 4.5 Units

| Unit | Kind | Responsibility |
|---|---|---|
| `HeldAccountStore` | new, SharedPreferences | Stores one held account: user id and nullable email. `get()`, `hold(account)`, `clear()`. |
| `HeldAccounts` | new, plain class | The decisions of §4.2 and §4.3. It takes the store, a count function and a delete function, so a JVM test can drive it. |
| `AppContainer` | changed | Counts the unsynced changes across the six repositories. Splits `clearAuthenticatedUserData` so the row delete is a separate function. Calls `HeldAccounts` from `handleSessionExpired` and from `migrateLocalToAuthenticated`. Exposes the held account summary for the Auth screen. |
| `sessionExpiredEvents` | changed | Carries the unsynced count instead of `Unit`. |
| `AppNavigation` | changed | Shows the toast text of §4.4. |
| `AuthViewModel`, `AuthScreen` | changed | Load the held account summary, show the notice card and fill the email field. |

## 5. Error handling

- **The count fails** (a database error): the app keeps the rows and writes the held account. A false keep costs only disk space. A false delete loses data. The toast uses the text for an unknown count.
- **The delete fails when the count is 0:** the app logs the error and continues the sign-out, as today.
- **The process dies after the hold and before the sign-out:** at the next start, the supabase-kt session is not valid. The next sync fails with "Session expired" again, and `handleSessionExpired` runs again. The store already holds the same user, so the second hold writes the same values.
- **The row delete for a different account fails:** the app logs the error and does not clear the held account. The sign-in continues. The next sign-in tries the delete again.
- **The user stays a guest:** the kept rows stay on the phone with no time limit. They use little space.

## 6. Testing

**`HeldAccountsTest`** (JVM, fakes for the store, the count and the delete):

1. An expiry with 0 unsynced changes deletes the rows and holds no account.
2. An expiry with 3 unsynced changes keeps the rows, holds the user and returns 3.
3. An expiry where the count throws keeps the rows and holds the user.
4. A sign-in as the held user keeps the rows and clears the hold.
5. A sign-in as another user deletes the rows of the held user and clears the hold.
6. A sign-in where the delete throws keeps the hold.
7. A sign-in with no hold deletes nothing.
8. The summary returns the held email and the current count, or null with no hold.

**`HeldAccountStoreTest`** (Robolectric): hold and get, a null email, clear.

**Copy tests:** the toast text and the notice text for 0, 1 and 3 changes, with and without an email. They are pure functions next to the composables.

**Device check** (local Supabase):

1. Sign in. Turn on airplane mode. Log 3 habits.
2. Revoke the session: delete the rows of the user from `auth.refresh_tokens` and `auth.sessions` in local Studio.
3. Turn off airplane mode, and start a sync from Today.
4. Expect the toast `Session expired. Sign in again to save 3 changes.` and the notice on the Auth screen.
5. Sign in with the same account. Expect the 3 logs on the server, and Today with the 3 logs.
6. Do steps 1 to 4 again, then sign in with a second account. Expect Today of the second account with none of the 3 logs.

## 7. Risks

- **Guest rows after the expiry.** A guest who logs habits after the expiry, then signs in with the held account, follows the current guest rules: the app deletes those rows if the server has data for the account. That is the behaviour today, and this change does not touch it.
- **A wrong email on the notice.** The email comes from the session at the time of the expiry. If supabase-kt has no user at that time, the notice uses the text without an email.
