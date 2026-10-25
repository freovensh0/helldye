package dev.scdeal.helldye.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayUrlPolicyTest {
    @Test
    fun `https is accepted in both build types`() {
        assertNull(GatewayUrlPolicy.validate("https://phone.tailnet.ts.net", debug = false))
        assertNull(GatewayUrlPolicy.validate("https://phone.tailnet.ts.net", debug = true))
    }

    @Test
    fun `cleartext is only accepted for loopback`() {
        assertNull(GatewayUrlPolicy.validate("http://127.0.0.1:8787", debug = true))
        assertNull(GatewayUrlPolicy.validate("http://localhost:8787", debug = true))
        assertNull(GatewayUrlPolicy.validate("http://127.0.0.1:8787", debug = false))
        assertFalse(GatewayUrlPolicy.validate("http://192.168.1.4:8787", debug = true) == null)
        assertFalse(GatewayUrlPolicy.validate("http://example.com", debug = false) == null)
    }

    @Test
    fun `invalid and unsupported URLs are rejected`() {
        assertFalse(GatewayUrlPolicy.validate("", debug = true) == null)
        assertFalse(GatewayUrlPolicy.validate("192.168.1.4:8787", debug = true) == null)
        assertFalse(GatewayUrlPolicy.validate("ftp://example.com", debug = false) == null)
    }

    @Test
    fun `loopback detection covers ipv6 brackets`() {
        assertTrue(GatewayUrlPolicy.isLoopback("localhost"))
        assertTrue(GatewayUrlPolicy.isLoopback("127.0.0.1"))
        assertTrue(GatewayUrlPolicy.isLoopback("[::1]"))
        assertFalse(GatewayUrlPolicy.isLoopback("192.168.1.4"))
    }
}
