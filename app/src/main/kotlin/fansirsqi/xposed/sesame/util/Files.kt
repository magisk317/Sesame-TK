package fansirsqi.xposed.sesame.util

import java.io.File

/** Fields required by checker JNI_OnLoad while the native source is unavailable. */
object Files {
    @JvmField
    val CONFIG_DIR: File = io.github.magisk317.sesame.util.Files.CONFIG_DIR
}
