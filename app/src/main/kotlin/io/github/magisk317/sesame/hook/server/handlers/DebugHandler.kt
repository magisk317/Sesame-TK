package io.github.magisk317.sesame.hook.server.handlers

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.hook.server.ServerCommon.MIME_JSON
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response

class DebugHandler(secretToken: String) : BaseHandler(secretToken) {

    override fun onPost(session: IHTTPSession, body: String?): Response {
        if (body.isNullOrBlank()) {
            return badRequest("Empty body")
        }

        val request: RpcRequest = try {
            JsonUtil.decode(body, RpcRequest.serializer())
        } catch (e: Exception) {
            return badRequest("Invalid JSON: ${e.message}")
        }

        val dataStr = request.getRequestDataString()

        if (request.methodName.isBlank() || dataStr.isBlank()) {
            return badRequest("Fields cannot be empty")
        }

        return try {
            val result = if (request.facadeName.isNullOrBlank()) {
                RequestManager.requestString(request.methodName, dataStr)
            } else {
                RequestManager.requestString(
                    request.methodName,
                    dataStr,
                    request.appName,
                    request.operation,
                    request.facadeName,
                )
            }

            if (result.isBlank()) {
                json(Response.Status.OK, mapOf("status" to "empty"))
            } else {
                NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
            }
        } catch (e: Exception) {
            badRequest("RPC Error: ${e.message}")
        }
    }
}
