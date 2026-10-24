package io.github.magisk317.sesame.hook.internal

import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.service.HostRuntimeStore
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

object LocationHelper {

    private const val TAG = "LocationInfoHelper"
    private var classLoader: ClassLoader? = null
    private const val LOCATION_KEY = "cached_location"

    // 保留旧的回调接口以兼容 Java 代码，但已标记为 Deprecated
    fun interface LocationCallback {
        fun onLocationResult(location: JsonObject?)
    }

    fun init(loader: ClassLoader) {
        classLoader = loader
    }

    /**
     * 同步获取缓存的位置信息
     */
    fun getLocation(): JsonObject? {
        return try {
            HostRuntimeStore.getString(HostRuntimeStore.LOCATION_KEY)?.toJsonObject()
        } catch (e: Exception) {
            StructuredLog.error(TAG, "读取位置缓存失败: ${e.message}")
            null
        }
    }

    /**
     * ✓ 新增：挂起函数版本 (推荐 Kotlin 使用)
     * 在后台线程获取位置并返回结果，自动切回原线程
     */
    suspend fun requestLocationSuspend(): JsonObject = withContext(Dispatchers.Default) {
        try {
            if (classLoader == null) {
                return@withContext createAndSaveError("ClassLoader 未初始化")
            }

            // 执行反射调用 (耗时操作)
            val lnsctrUtilsClass = HookHelpers.findClass("com.alipay.mobile.common.lnsctr.LnsctrUtils", classLoader)
            val latitude = HookHelpers.callStaticMethod(lnsctrUtilsClass, "getLatitude") as? Double
            val longitude = HookHelpers.callStaticMethod(lnsctrUtilsClass, "getLongitude") as? Double

            if (latitude != null && longitude != null) {
                val locationMap = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
                saveLocation(locationMap)
                jsonObject(*locationMap.map { (key, value) -> key to value }.toTypedArray())
            } else {
                createAndSaveError("等待目标应用初始化中...")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            StructuredLog.error(TAG, "获取经纬度异常: ${e.message}")
            createAndSaveError("获取失败: ${e.message}")
        }
    }

    /**
     * 兼容旧版：回调风格 (内部使用协程)
     * 供 Java 代码或尚未迁移的 Kotlin 代码使用
     */
    fun requestLocation(callback: LocationCallback) {
        GlobalThreadPools.execute(context = Dispatchers.Main) {
            val result = requestLocationSuspend()
            callback.onLocationResult(result)
        }
    }

    private fun createAndSaveError(msg: String): JsonObject {
        val map = mapOf("status" to msg)
        saveLocation(map)
        return jsonObject(*map.map { (key, value) -> key to value }.toTypedArray())
    }

    private fun saveLocation(locationMap: Map<String, Any>) {
        try {
            HostRuntimeStore.putString(HostRuntimeStore.LOCATION_KEY, jsonObject(*locationMap.map { (key, value) -> key to value }.toTypedArray()).toString())
        } catch (e: Exception) {
            StructuredLog.error(TAG, "保存位置缓存失败: ${e.message}")
        }
    }
}
