package fansirsqi.xposed.sesame.util

/** Minimal legacy JNI lookup surface. */
object FansirsqiUtil {
    fun getPackageName(): String = io.github.magisk317.sesame.data.General.MODULE_PACKAGE_NAME

    fun getFolderList(folderPath: String): List<String> =
        io.github.magisk317.sesame.util.FansirsqiUtil.getFolderList(folderPath)
}
