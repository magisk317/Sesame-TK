package io.github.magisk317.sesame.hook.server

import io.github.magisk317.sesame.hook.server.handlers.DebugHandler
import io.github.magisk317.sesame.hook.server.handlers.HttpHandler
import io.github.magisk317.sesame.util.StructuredLog
import fi.iki.elonen.NanoHTTPD
import java.io.DataInputStream
import java.nio.charset.StandardCharsets

class ModuleHttpServer(
    port: Int = 8080,
    secretToken: String = ""
) : NanoHTTPD("127.0.0.1", port) {
    // 绑定 127.0.0.1（仅 loopback）：避免在支付宝进程内暴露可被同网络/同设备任意 app
    // 访问的 RPC 代理。原 "0.0.0.0" 绑定 + 公开 token 等于把登录态支付宝 RPC 暴露给局域网。
    private val tag = "ModuleHttpServer"

    private val routes = mutableMapOf<String, HttpHandler>()

    init {
        // 注册路由
        register("/debugHandler", DebugHandler(secretToken), "调试接口")
    }

    @Suppress("SameParameterValue")
    private fun register(path: String, handler: HttpHandler, description: String = "") {
        StructuredLog.record(tag, "Registering handler : $path -> $description")
        routes[path] = handler
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val handler = routes[uri] ?: return notFound()

        return try {
            // 如果是 POST/PUT 请求，安全读取 Body
            var body: String? = null
            if (session.method == Method.POST || session.method == Method.PUT) {
                body = getPostBodySafe(session)
            }
            handler.handle(session, body)
        } catch (e: Exception) {
            // 🔥 全局异常捕获，防止 Handler 内部崩溃导致 Socket 中断
            StructuredLog.printStackTrace(tag, "Server Error on $uri", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, ServerCommon.MIME_PLAINTEXT, "Internal Server Error: ${e.message}")
        }
    }

    /**
     * 🔥【关键修复】安全读取 Body
     * 1. 确保读满 content-length 长度的数据
     * 2. 指定 UTF-8 编码
     */
    private fun getPostBodySafe(session: IHTTPSession): String? {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (contentLength == 0) return ""

        return try {
            val buffer = ByteArray(contentLength)
            val inputStream = DataInputStream(session.inputStream)

            // 使用 readFully 确保读满所有字节，不够会阻塞等待，直到读完或超时
            inputStream.readFully(buffer)

            // 明确使用 UTF-8，防止中文乱码
            String(buffer, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(tag, "Failed to read body", e)
            null
        }
    }

    private fun notFound(): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, ServerCommon.MIME_PLAINTEXT, "Not Found")
    }
}