package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Monthly member-sticker query and claim RPCs. */
object MemberStickerRpc {
    fun queryCanReceive(year: String, month: String): String = request(
        "alipay.memberasset.sticker.queryStickerCanReceive",
        jsonObject("isFirstShow" to "false", "month" to month, "scene" to "", "year" to year),
    )

    fun receive(year: String, month: String, stickerIds: List<String>): String? {
        if (stickerIds.isEmpty()) return null
        return request(
            "alipay.memberasset.sticker.receiveSticker",
            jsonObject("month" to month, "stickerIds" to jsonArray(*stickerIds.map(::JsonPrimitive).toTypedArray()), "year" to year),
        )
    }

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
