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
