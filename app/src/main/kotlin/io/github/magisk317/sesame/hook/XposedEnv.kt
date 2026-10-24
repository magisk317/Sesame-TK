package io.github.magisk317.sesame.hook

import android.content.pm.ApplicationInfo


object XposedEnv {
    lateinit var classLoader: ClassLoader
    lateinit var appInfo: ApplicationInfo
    lateinit var packageName: String
    lateinit var processName: String
}
