package io.github.magisk317.sesame.xp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LibXposedManifestContractTest {
    @Test
    fun `Sesame uses the libxposed 102 declaration surface`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val moduleProp = projectFile("app/src/main/resources/META-INF/xposed/module.prop").readText()
        val javaInit = projectFile("app/src/main/resources/META-INF/xposed/java_init.list").readText()

        assertTrue(moduleProp.lines().contains("minApiVersion=102"))
        assertTrue(moduleProp.lines().contains("targetApiVersion=102"))
        assertTrue(javaInit.lines().contains("io.github.magisk317.sesame.xp.LibXposedEntry"))
        assertTrue(manifest.contains("io.github.libxposed.service.XposedProvider"))
        assertFalse(manifest.contains("xposedmodule"))
        assertFalse(manifest.contains("xposeddescription"))
        assertFalse(manifest.contains("xposedminversion"))
        assertFalse(manifest.contains("xposedscope"))
        assertFalse(manifest.contains("de.robv.android.xposed.category.MODULE_SETTINGS"))
    }

    private fun projectFile(relativePath: String): File {
        val direct = File(relativePath)
        val file = if (direct.isFile) direct else File("../$relativePath")
        require(file.isFile) { "Cannot resolve project file: $relativePath" }
        return file
    }
}
