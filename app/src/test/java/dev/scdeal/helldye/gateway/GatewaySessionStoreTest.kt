package dev.scdeal.helldye.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewaySessionStoreTest {
    @Test
    fun `a cursor resumes within the same epoch`() {
        assertEquals("42", GatewaySessionStore.reconcileCursor("epoch-a", "epoch-a", "42"))
    }

    @Test
    fun `a cursor is discarded when the gateway epoch changes`() {
        assertNull(GatewaySessionStore.reconcileCursor("epoch-a", "epoch-b", "42"))
    }

    @Test
    fun `an unknown epoch keeps the cursor for the transcript snapshot to correct`() {
        assertEquals("42", GatewaySessionStore.reconcileCursor(null, "epoch-b", "42"))
        assertEquals("42", GatewaySessionStore.reconcileCursor("epoch-a", null, "42"))
    }
}
