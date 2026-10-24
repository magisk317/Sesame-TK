package fansirsqi.xposed.sesame.util

import android.content.Context

/**
 * JNI compatibility surface for the prebuilt checker library.
 *
 * The library is still linked against the historical class name. Keep this
 * class deliberately small; application code must use the relocated facade.
 */
object Detector {
    @JvmStatic
    external fun init(context: Context)

    @JvmStatic
    external fun tips(context: Context, message: String?)

    @JvmStatic
    external fun isEmbeddedNative(context: Context): Boolean

    @JvmStatic
    external fun dangerous(context: Context)

    @JvmStatic
    external fun getApiUrlWithKey(key: Int): String

    @JvmStatic
    external fun loadLibraryWithContextNative(context: Context, libraryName: String): Boolean
}
