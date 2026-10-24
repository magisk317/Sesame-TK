package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.data.Status.Companion.hasFlagToday
import io.github.magisk317.sesame.data.Status.Companion.setFlagToday
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Queries and claims the daily member sticker rewards. */
class MemberStickerService {
    fun queryAndCollect() {
        try {
            if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_STICKER)) {
                StructuredLog.record(TAG, "今日已兑换贴纸，跳过")
                return
            }
            val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
            val month = SimpleDateFormat("MM", Locale.ENGLISH).format(Date())
            val query = MemberStickerRpc.queryCanReceive(year, month).toJsonObject()
            if (!ResChecker.checkRes(TAG, query)) {
                StructuredLog.error(TAG, "查询贴纸失败：$query")
                return
            }
            val pages = query.optJSONArray("canReceivePageList") ?: return
            val names = mutableMapOf<String, String>()
            val ids = mutableListOf<String>()
            for (pageIndex in pages.indices) {
                val stickers = pages.optJSONObject(pageIndex)?.optJSONArray("stickerCanReceiveList") ?: continue
                for (stickerIndex in stickers.indices) {
                    val sticker = stickers.optJSONObject(stickerIndex) ?: continue
                    val id = sticker.optString("id")
                    if (id.isNotEmpty()) {
                        ids += id
                        names[id] = sticker.optString("name").ifEmpty { "未知贴纸" }
                    }
                }
            }
            if (ids.isEmpty()) {
                StructuredLog.record(TAG, "贴纸扫描：暂无可领取的贴纸")
                return
            }
            val result = MemberStickerRpc.receive(year, month, ids)?.toJsonObject() ?: return
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.error(TAG, "领取贴纸失败：$result")
                return
            }
            val special = result.optJSONArray("specialStickerList")
            val obtained = result.optJSONArray("obtainedConfigId")
            StructuredLog.record(TAG, "贴纸领取成功，总数：${obtained?.size ?: 0}")
            if (special != null) {
                for (index in special.indices) {
                    val item = special.optJSONObject(index) ?: continue
                    val name = names[item.optString("stickerRecordId")] ?: "特殊贴纸"
                    StructuredLog.other(TAG, "获得特殊贴纸 → $name (${item.optString("rankingText")})")
                }
            }
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_STICKER)
        } catch (error: Exception) {
            StructuredLog.printStackTrace(TAG, "stickerAutoCollect err", error)
        }
    }

    private companion object {
        const val TAG = "MemberStickerService"
    }
}
