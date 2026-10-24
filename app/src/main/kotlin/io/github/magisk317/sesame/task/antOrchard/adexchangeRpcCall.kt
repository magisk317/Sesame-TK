package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import java.net.URLDecoder

object XLightRpcCall {

    // 固定 SDK 信息
    private const val AD_COMPONENT_TYPE = "FEEDS"
    private const val AD_COMPONENT_VERSION = "4.28.66"
    private const val ENABLE_FUSION = true
    private const val NETWORK_TYPE = "WIFI"
    private const val PAGE_NO = 1
    private const val UNION_APP_ID = "2060090000304921"
    private const val XLIGHT_RUNTIME_SDK_VERSION = "4.28.66"
    private const val XLIGHT_SDK_TYPE = "h5"
    private const val XLIGHT_SDK_VERSION = "4.28.66"

    /**
     * 调用 xlightPlugin
     * @param referToken referToken 字符串
     * @param pageUrl 当前页面 url
     * @param pageFrom 页面来源
     */
    fun xlightPlugin(
        pageUrl: String,
        pageFrom: String,
        session: String,
        spaceCode: String
    ): String {
        return try {

            // positionRequest
            val positionRequest = jsonObject(
                "extMap" to jsonObject(), "referInfo" to jsonObject(),
                "searchInfo" to jsonObject(), "spaceCode" to spaceCode,
            )

            // sdkPageInfo
            val sdkPageInfo = jsonObject(
                "adComponentType" to AD_COMPONENT_TYPE, "adComponentVersion" to AD_COMPONENT_VERSION,
                "enableFusion" to ENABLE_FUSION, "networkType" to NETWORK_TYPE,
                "pageFrom" to pageFrom, "pageNo" to PAGE_NO, "pageUrl" to pageUrl,
                "session" to session, "unionAppId" to UNION_APP_ID, "usePlayLink" to "true",
                "xlightRuntimeSDKversion" to XLIGHT_RUNTIME_SDK_VERSION,
                "xlightSDKType" to XLIGHT_SDK_TYPE, "xlightSDKVersion" to XLIGHT_SDK_VERSION,
            )

            // 数组包装
            val args = jsonArray(jsonObject("positionRequest" to positionRequest, "sdkPageInfo" to sdkPageInfo))

            // RPC 调用
            RequestManager.requestString(
                "com.alipay.adexchange.ad.facade.xlightPlugin",
                args.toString()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 完成广告任务（新版，支持 extendInfo）
     * @param playBizId 广告任务业务 ID
     * @param playEventInfo 完整的 playEventInfo JSON
     * @param iepTaskSceneCode extendInfo.iepTaskSceneCode
     * @param iepTaskType extendInfo.iepTaskType
     */
    fun finishTask(
        playBizId: String,
        playEventInfo: JsonObject,
        iepTaskSceneCode: String,
        iepTaskType: String
    ): String {
        return try {

            // extendInfo
            val extendInfo = jsonObject("iepTaskSceneCode" to iepTaskSceneCode, "iepTaskType" to iepTaskType)

            // 单条任务对象
            val args = jsonObject(
                "extendInfo" to extendInfo, "playBizId" to playBizId,
                "playEventInfo" to playEventInfo, "source" to "adx",
            )

            // 最外层数组
            val argsArray = jsonArray(args)

            RequestManager.requestString(
                "com.alipay.adtask.biz.mobilegw.service.interaction.finish",
                argsArray.toString()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

}

object UrlUtil {
    /**
     * 从原始URL中提取指定参数的完整值(支持多层嵌套)
     * @param url 原始URL
     * @param key 要提取的参数名
     * @return 完整的参数值(已解码)
     */
    fun getParamValue(url: String, key: String): String? {
        if (url.isEmpty()) return null

        try {
            // 先多次解码展开
            var decoded = url
            repeat(5) {
                val temp = decode(decoded)
                if (temp == decoded) return@repeat
                decoded = temp
            }

            // 找到查询字符串部分
            val queryStart = decoded.indexOf("?")
            if (queryStart == -1) return null

            val query = decoded.substring(queryStart + 1)

            // 使用正则表达式精确匹配参数,避免截断
            val pattern = Regex("(?:^|&)$key=([^&]*)")
            val match = pattern.find(query)

            return match?.groupValues?.get(1)?.let { decode(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 提取URL中指定参数后面的完整嵌套URL
     * @param url 原始URL
     * @param key 触发参数名(比如"url")
     * @return 完整的嵌套URL(已解码)
     */
    fun getFullNestedUrl(url: String, key: String): String? {
        if (url.isEmpty()) return null

        try {
            // 先解码原始URL
            var decoded = url
            repeat(5) {
                val temp = decode(decoded)
                if (temp == decoded) return@repeat
                decoded = temp
            }

            // 查找key=的位置
            val searchKey = "$key="
            val keyIndex = decoded.indexOf(searchKey)
            if (keyIndex == -1) return null

            // 从key=之后开始提取
            val startIndex = keyIndex + searchKey.length
            val remaining = decoded.substring(startIndex)

            // 找到参数值的结束位置
            var endIndex = remaining.length

            // 如果是URL类型,需要找到完整URL的边界
            if (remaining.startsWith("http://") || remaining.startsWith("https://")) {
                // 查找下一个顶层&符号(在URL外部的&)
                // 策略: 检测到&后,判断其后是否跟着已知的顶层参数名
                val topLevelParams = listOf("canPullDown=", "showOptionMenu=", "iepTaskType=",
                    "iepTaskSceneCode=", "canDoTask=", "awardCount=", "doneTimes=")

                for (i in remaining.indices) {
                    if (remaining[i] == '&') {
                        // 检查&后面是否是顶层参数
                        val afterAmp = remaining.substring(i + 1)
                        if (topLevelParams.any { afterAmp.startsWith(it) }) {
                            endIndex = i
                            break
                        }
                    }
                }
            } else {
                // 非URL类型,找第一个&
                val ampIndex = remaining.indexOf("&")
                if (ampIndex != -1) {
                    endIndex = ampIndex
                }
            }

            val value = remaining.substring(0, endIndex)

            // 再次解码确保完全展开
            var result = value
            repeat(5) {
                val temp = decode(result)
                if (temp == result) return@repeat // 替代break，终止当前repeat迭代
                result = temp
            }


            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 从完整URL中提取指定参数
     * @param fullUrl 完整的URL
     * @param key 参数名
     * @return 参数值
     */
    fun extractParamFromUrl(fullUrl: String, key: String): String? {
        if (fullUrl.isEmpty()) return null

        try {
            val queryStart = fullUrl.indexOf("?")
            if (queryStart == -1) return null

            val query = fullUrl.substring(queryStart + 1)

            // 使用正则精确匹配
            val pattern = Regex("(?:^|&)$key=([^&]*)")
            val match = pattern.find(query)

            return match?.groupValues?.get(1)?.let { decode(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 安全解码URL
     */
    fun decode(url: String): String {
        return try {
            URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
    }

    /**
     * 批量解码直到稳定
     */
    fun decodeUntilStable(url: String): String {
        var current = url
        var last: String
        var count = 0
        do {
            last = current
            current = decode(current)
            count++
        } while (current != last && count < 10)
        return current
    }
}
