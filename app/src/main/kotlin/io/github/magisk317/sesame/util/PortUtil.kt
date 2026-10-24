package io.github.magisk317.sesame.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.util.maps.CooperateMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.UserMap

/** Import/export boundary for configuration files and persisted user maps. */
object PortUtil {
    @JvmStatic
    fun handleExport(context: Context, uri: Uri?, userId: String?) {
        if (uri == null) {
            ToastUtil.makeText("未选择目标位置", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val configFile = if (userId.isNullOrEmpty()) Files.getDefaultConfigFile() else Files.getConfigFile(userId)
            val exported = configFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(uri)?.use { output -> Files.streamTo(input, output) } ?: false
            }
            ToastUtil.makeText(if (exported) "导出成功！" else "导出失败！", Toast.LENGTH_SHORT).show()
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(error)
            ToastUtil.makeText("导出失败：发生异常", Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun handleImport(context: Context, uri: Uri?, userId: String?) {
        if (uri == null) {
            ToastUtil.makeText("导入失败：未选择文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val configFile = if (userId.isNullOrEmpty()) Files.getDefaultConfigFile() else Files.getConfigFile(userId)
            val imported = context.contentResolver.openInputStream(uri)?.use { input ->
                configFile.outputStream().use { output -> Files.streamTo(input, output) }
            } ?: false
            if (!imported) {
                ToastUtil.makeText("导入失败！", Toast.LENGTH_SHORT).show()
                return
            }
            ToastUtil.makeText("导入成功！", Toast.LENGTH_SHORT).show()
            if (!userId.isNullOrEmpty()) {
                runCatching {
                    context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.restart").apply {
                        setPackage(General.PACKAGE_NAME)
                        putExtra("userId", userId)
                    })
                }.onFailure(StructuredLog::printStackTrace)
            }
            val activity = context as? Activity ?: return
            val intent = activity.intent
            activity.finish()
            context.startActivity(intent)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(error)
            ToastUtil.makeText("导入失败：发生异常", Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun save(context: Context, userId: String?) {
        try {
            if (Config.isModify(userId) && Config.save(userId, false)) {
                ToastUtil.showToastWithDelay("保存成功！", 100)
                if (!userId.isNullOrEmpty()) {
                    context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.restart").apply {
                        setPackage(General.PACKAGE_NAME)
                        putExtra("userId", userId)
                    })
                }
            }
            if (!userId.isNullOrEmpty()) {
                UserMap.save(userId)
                IdMapManager.getInstance(CooperateMap::class.java).save(userId)
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(error)
        }
    }
}
