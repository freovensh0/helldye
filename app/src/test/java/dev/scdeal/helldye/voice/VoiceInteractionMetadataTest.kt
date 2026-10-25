package dev.scdeal.helldye.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The platform VoiceInteractionServiceInfo parser requires both sessionService and
 * recognitionService and marks the whole component invalid when either is missing, which breaks the
 * assist gesture. These attributes are contract, not options.
 */
class VoiceInteractionMetadataTest {
    @Test
    fun `voice interaction metadata names both platform services`() {
        val root = parse("src/main/res/xml/helldye_voice_interaction.xml").documentElement
        assertNotNull(root.getAttributeNodeNS(ANDROID, "sessionService"))
        assertNotNull(root.getAttributeNodeNS(ANDROID, "recognitionService"))
        assertEquals(
            "dev.scdeal.helldye.voice.HelldyeRecognitionService",
            root.getAttributeNS(ANDROID, "recognitionService"),
        )
    }

    @Test
    fun `manifest declares the bound recognition service`() {
        val services = parse("src/main/AndroidManifest.xml").getElementsByTagName("service")
        val recognition = (0 until services.length)
            .map { services.item(it) as Element }
            .firstOrNull {
                it.getAttributeNS(ANDROID, "name") == ".voice.HelldyeRecognitionService"
            } ?: error("the manifest does not declare .voice.HelldyeRecognitionService")
        assertEquals(
            "android.permission.BIND_RECOGNITION_SERVICE",
            recognition.getAttributeNS(ANDROID, "permission"),
        )
    }

    private fun parse(path: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(sourceFile(path))

    // Unit tests run from the module directory; accept the repository root too.
    private fun sourceFile(path: String): File {
        val module = File(System.getProperty("user.dir"))
        return listOf(module, File(module, "app"))
            .map { File(it, path) }
            .firstOrNull { it.isFile }
            ?: error("cannot find $path from $module")
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
