package io.github.magisk317.sesame.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import io.github.magisk317.sesame.model.BaseModel
import java.util.Locale

/** Applies the configured application locale. */
object LanguageUtil {
    @JvmStatic
    @SuppressLint("AppBundleLocaleChanges")
    fun setLocale(context: Context) {
        if (!BaseModel.languageSimplifiedChinese.value) return
        val locale = Locale.Builder().setLanguage("zh").setRegion("CN").build()
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }
}
