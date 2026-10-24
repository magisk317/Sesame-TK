package fansirsqi.xposed.sesame.ui.extension

import android.content.Context

/** Forwards the old JNI callback name to the relocated Compose bridge. */
object NativeComposeBridge {
    @JvmStatic
    fun showAlertDialog(
        context: Context,
        title: String,
        message: String,
        buttonText: String
    ) {
        io.github.magisk317.sesame.ui.extension.NativeComposeBridge.showAlertDialog(
            context, title, message, buttonText
        )
    }

    @JvmStatic
    fun showAlertAfterDelay(
        context: Context,
        title: String,
        message: String,
        buttonText: String,
        delayMillis: Long
    ) {
        io.github.magisk317.sesame.ui.extension.NativeComposeBridge.showAlertAfterDelay(
            context, title, message, buttonText, delayMillis
        )
    }
}
