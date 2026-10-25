package dev.scdeal.helldye

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Release transport stays HTTPS-only; debug cleartext is loopback-only. */
class SecurityConfigTest {
    @Test
    fun `release manifest forbids cleartext`() {
        val manifest = source("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(manifest.contains("android:usesCleartextTraffic=\"true\""))
    }

    @Test
    fun `base network config denies cleartext`() {
        val config = source("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"false\""))
    }

    @Test
    fun `debug cleartext is limited to loopback`() {
        val debug = source("src/debug/res/xml/network_security_config.xml").readText()
        assertTrue(debug.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertTrue(debug.contains("127.0.0.1"))
        assertTrue(debug.contains("localhost"))
    }

    private fun source(path: String): File {
        val module = File(System.getProperty("user.dir"))
        return listOf(module, File(module, "app"))
            .map { File(it, path) }
            .firstOrNull { it.isFile }
            ?: error("cannot find $path from $module")
    }
}
