package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.hook.internal.AlipayMiniMarkHelper
import io.github.magisk317.sesame.hook.internal.AuthCodeHelper
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

enum class GameTask(
    val title: String,
    val appId: String,
    val gid: String,
    val action: String,
    val channel: String,
    val version: String,
    val requestsPerEgg: Int //完成1个🥚要多少次 为了防止网络崩溃 多加1次
) {
    Orchard_ncscc("农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 2),

    Forest_slxcc("森林小车车","2060170000363691","zfb_slxcc","slxcc_game_kaiche_every_10","lianyun_senlin_leyuan","1.0.1",3),
    Forest_sljyd("森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    /**
     * 第一步：登录获取 Token 并缓存
     */
    private fun login(): String? {
        return try {
            val loader = ApplicationHook.classLoader
            if (loader == null) {
                StructuredLog.error(title, "登录失败 [LOGIN_CLASS_LOADER_EMPTY]")
                return null
            }
            // Application.attach is not guaranteed to run before scheduled work in a reused process.
            // Re-initialization is idempotent and ensures both reflection helpers use this process loader.
            AuthCodeHelper.init(loader)
            AlipayMiniMarkHelper.init(loader)
            val authCode = AuthCodeHelper.getAuthCode(appId)
            if (authCode.isNullOrBlank()) {
                StructuredLog.error(title, "登录失败 [LOGIN_AUTH_CODE_EMPTY] appId=$appId")
                return null
            }
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            if (mark.isBlank()) {
                StructuredLog.error(title, "登录失败 [LOGIN_MINI_MARK_EMPTY] appId=$appId version=$version")
                return null
            }
            val reqId = "${System.currentTimeMillis()}_${(1..350).random()}"

            val body = jsonObject(
                "v" to version, "code" to authCode, "pf" to "zfb",
                "reqId" to reqId, "gid" to gid, "version" to version,
            ).toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/game/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 💡 改进：登录失败也要读错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "EMPTY"

            val resJson = responseText.toJsonObject()
            val businessCode = resJson.optInt("code")
            if (respCode !in 200..299) {
                StructuredLog.error(title, "登录失败 [LOGIN_HTTP_ERROR] http=$respCode code=$businessCode msg=${responseMessage(resJson)}")
                null
            } else if (businessCode == 1) {
                val token = resJson.optJSONObject("data")?.optString("token")
                if (token.isNullOrBlank()) {
                    StructuredLog.error(title, "登录失败 [LOGIN_TOKEN_MISSING] http=$respCode code=$businessCode msg=${responseMessage(resJson)}")
                    null
                } else {
                    StructuredLog.other(title, "登录成功，已获取会话 Token")
                    token
                }
            } else {
                StructuredLog.error(title, "登录失败 [LOGIN_BUSINESS_ERROR] http=$respCode code=$businessCode msg=${responseMessage(resJson)}")
                null
            }
        } catch (e: Exception) {
            StructuredLog.e(title, "登录失败 [LOGIN_EXCEPTION] ${e.javaClass.simpleName}: ${e.message ?: "无错误信息"}", e)
            null
        }
    }

    /**
     * 外部调用：执行上报任务
     */
    fun report(eggCount: Int) {
        val totalNeeded = eggCount * (requestsPerEgg+1)//正常不需要加1，多1次确保网络请求不会错误
        GlobalThreadPools.currentScope().launch {
            val token = login()
            if (token.isNullOrEmpty()) {
                StructuredLog.record(title, "⚠️ 无法获取有效的 Token，放弃上报任务")
                return@launch
            }

            for (i in 1..totalNeeded) {
                if (!executeSingleReport(token, i, totalNeeded)) {
                    break
                }
                if (i < totalNeeded) delay((1000..3000).random().toLong())
            }
        }
    }

    private fun executeSingleReport(token: String, current: Int, total: Int): Boolean {
        return try {
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            val body = jsonObject(
                "v" to version, "version" to version,
                "reqId" to "${System.currentTimeMillis()}_${(10..99).random()}",
                "gid" to gid, "action_code" to action, "action_finish_channel" to channel,
            ).toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/zfb/taskReport").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                setRequestProperty("authorization", token)
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
                setRequestProperty("referer", "https://$appId.hybrid.alipay-eco.com/$appId/$version/index.html")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 💡 重点改进：读取响应码并捕获错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "NULL_RESPONSE"

            val resJson = responseText.toJsonObject()
            val businessCode = resJson.optInt("code")
            if (respCode !in 200..299) {
                StructuredLog.error(title, "第 $current 次上报失败 [REPORT_HTTP_ERROR] http=$respCode code=$businessCode msg=${responseMessage(resJson)}")
                false
            } else if (businessCode == 1) {
                if (current % requestsPerEgg == 0) StructuredLog.other(title, "📈 进度: $current/$total (已达成 ${current/requestsPerEgg} 个蛋)")
                true
            } else {
                StructuredLog.error(title, "第 $current 次上报失败 [REPORT_BUSINESS_ERROR] http=$respCode code=$businessCode msg=${responseMessage(resJson)}")
                false
            }
        } catch (e: Exception) {
            StructuredLog.e(title, "第 $current 次上报失败 [REPORT_EXCEPTION] ${e.javaClass.simpleName}: ${e.message ?: "无错误信息"}", e)
            false
        }
    }

    private fun responseMessage(response: JsonObject): String {
        return response.optString("msg").ifBlank { response.optString("message") }
            .take(160).replace(Regex("[\\r\\n]+"), " ").ifBlank { "无" }
    }

    private fun getDynamicUA(): String {
        val systemUa = System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android 11)"
        val alipayVer = ApplicationHook.alipayVersion
        return "$systemUa NebulaSDK/1.8.100112 Nebula AliApp(AP/$alipayVer) AlipayClient/$alipayVer"
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000
    }
}
