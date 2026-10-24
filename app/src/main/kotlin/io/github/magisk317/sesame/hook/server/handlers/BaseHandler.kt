package io.github.magisk317.sesame.hook.server.handlers

import io.github.magisk317.sesame.hook.server.ServerCommon
import io.github.magisk317.sesame.util.JsonUtil
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.security.MessageDigest

abstract class BaseHandler(private val secretToken: String) : HttpHandler {

    // 直接使用单例

    /**
     * 模板方法：统一处理鉴权和异常
     */
    final override fun handle(session: IHTTPSession, body: String?): Response {
        return try {
            if (!verifyToken(session)) {
                return unauthorized()
            }

            when (session.method) {
                Method.GET -> onGet(session)
                Method.POST -> onPost(session, body)
                else -> methodNotAllowed()
            }
        } catch (e: Exception) {
            e.printStackTrace() // 打印堆栈到 Logcat
            // 不回传原始异常消息：原实现会把反射 class-not-found 等消息返回给客户端，
            // 配合公开 token 可被用于指纹宿主支付宝版本。返回固定文案。
            json(Response.Status.INTERNAL_ERROR, mapOf("status" to "error", "message" to "Internal Server Error"))
        }
    }

    private fun verifyToken(session: IHTTPSession): Boolean {
        // fail-closed：未配置 token 一律拒绝。原实现 secretToken.isBlank() -> true 是 fail-open。
        if (secretToken.isBlank()) return false

        val authHeader = session.headers["authorization"] ?: return false
        // 允许 Bearer 或直接 Token
        val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            authHeader.trim()
        }
        // 常数时间比较：避免计时侧信道（即便 token 已不可提取也作纵深防御）。
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            secretToken.toByteArray(Charsets.UTF_8),
        )
    }

    // 默认实现改为 405 Method Not Allowed，而不是 404 Not Found
    open fun onGet(session: IHTTPSession): Response = methodNotAllowed()
    open fun onPost(session: IHTTPSession, body: String?): Response = methodNotAllowed()

    // --- 响应辅助方法 ---

    protected fun json(status: Response.Status, data: Any): Response {
        val jsonText = data as? String ?: JsonUtil.formatJson(data, false)
        return NanoHTTPD.newFixedLengthResponse(status, ServerCommon.MIME_JSON, jsonText)
    }

    protected fun ok(data: Any): Response = json(Response.Status.OK, data)

    protected fun badRequest(message: String): Response =
        json(Response.Status.BAD_REQUEST, mapOf("status" to "error", "message" to message))

    protected fun unauthorized(): Response =
        json(Response.Status.UNAUTHORIZED, mapOf("status" to "unauthorized"))

    protected fun methodNotAllowed(): Response =
        json(Response.Status.METHOD_NOT_ALLOWED, mapOf("status" to "method_not_allowed"))

    protected fun notFound(): Response =
        json(Response.Status.NOT_FOUND, mapOf("status" to "not_found"))
}
