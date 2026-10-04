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
