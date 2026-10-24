package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns forest prop inventory reads and the consume protocol. */
class ForestPropService(
    private val refreshHome: () -> Unit,
    private val onPropName: (String) -> Unit,
    private val onError: (String, Throwable) -> Unit,
) {
    internal data class GiftCandidate(
        val giveConfigId: String,
        val propId: String,
        val propName: String,
        val holdsNum: Int,
    )

    private var cachedBag: JsonObject? = null
    private var lastQueryTime = 0L

    fun query(forceRefresh: Boolean = false): JsonObject? {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedBag != null && now - lastQueryTime < 5000) {
                return cachedBag
            }
            return try {
                StructuredLog.record(TAG, "刷新背包...")
                val response = ForestPropRpc.list(false)
                if (response.isNullOrBlank()) {
                    StructuredLog.record(TAG, "刷新背包失败: 响应为空")
                    null
                } else {
                    val bag = response.toJsonObject()
                    if (bag.optBoolean("success")) {
                        cachedBag = bag
                        lastQueryTime = now
                        bag
                    } else {
                        StructuredLog.record(TAG, "刷新背包失败: ${bag.optString("resultDesc")}")
                        null
                    }
                }
            } catch (t: Throwable) {
                onError("queryPropList", t)
                null
            }
        }
    }

    fun find(bag: JsonObject?, propType: String): JsonObject? {
        if (bag == null) return null
        return try {
            val props = bag.optJSONArray("forestPropVOList") ?: return null
            for (i in props.indices) {
                val prop = props.optJSONObject(i) ?: continue
                val currentType = prop.optJSONObject("propConfigVO")?.optString("propType")
                if (currentType == propType) return prop
            }
            null
        } catch (t: Throwable) {
            onError("findPropBag", t)
            null
        }
    }

    fun show() {
        val bag = query(true) ?: return
        try {
            val props = bag.optJSONArray("forestPropVOList") ?: return
            val output = StringBuilder("\n背包道具列表\n")
            for (i in props.indices) {
                val prop = props.optJSONObject(i) ?: continue
                val config = prop.optJSONObject("propConfigVO") ?: continue
                output.append("道具: ").append(config.optString("propName"))
                    .append(" | 数量: ").append(prop.optInt("holdsNum"))
                    .append(" | 类型: ").append(prop.optString("propType"))
                val expireTime = prop.optLong("recentExpireTime", 0)
                if (expireTime > 0) {
                    output.append(" | 过期时间: ").append(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(expireTime))
                    )
                }
                output.append('\n')
            }
            StructuredLog.record(TAG, output.toString())
        } catch (t: Throwable) {
            onError("showBag", t)
        }
    }

    internal fun giftCandidates(bag: JsonObject?): List<GiftCandidate> {
        val props = bag?.optJSONArray("forestPropVOList") ?: return emptyList()
        return buildList {
            for (index in props.indices) {
                val prop = props.optJSONObject(index) ?: continue
                val give = prop.optJSONObject("giveConfigVO") ?: continue
                val config = prop.optJSONObject("propConfigVO") ?: continue
                val ids = prop.optJSONArray("propIdList") ?: continue
                if (ids.isEmpty()) continue
                val giveConfigId = give.optString("giveConfigId")
                val propName = config.optString("propName")
                if (giveConfigId.isEmpty() || propName.isEmpty()) continue
                add(
                    GiftCandidate(
                        giveConfigId = giveConfigId,
                        propId = ids.getString(0),
                        propName = propName,
                        holdsNum = prop.optInt("holdsNum", 0),
                    ),
                )
            }
        }
    }

    fun give(targetUserId: String?) {
        if (targetUserId == null) return
        try {
            while (true) {
                val response = ForestPropRpc.list(true).toJsonObject()
                if (!ResChecker.checkRes(TAG + "查询道具列表失败:", response)) {
                    StructuredLog.record(TAG, "赠送道具查询结果" + response.optString("resultDesc"))
                    return
                }
                val candidates = giftCandidates(response)
                if (candidates.isEmpty()) return
                val candidate = candidates.first()
                val result = ForestPropRpc.give(
                    candidate.giveConfigId,
                    candidate.propId,
                    targetUserId,
                ).toJsonObject()
                if (ResChecker.checkRes(TAG + "赠送道具失败:", result)) {
                    StructuredLog.forest(
                        "赠送道具🎭[${UserMap.getMaskName(targetUserId)}]#${candidate.propName}",
                    )
                    GlobalThreadPools.sleepCompat(1500)
                } else {
                    val description = result.optString("resultDesc")
                    StructuredLog.record(description)
                    StructuredLog.record(result.toString())
                    if (description.contains("异常")) return
                }
                if (candidate.holdsNum <= 1 && candidates.size == 1) return
            }
        } catch (error: Throwable) {
            onError("giveProp", error)
        }
    }

    fun use(prop: JsonObject?, needRefreshHome: Boolean = true): Boolean {
        if (prop == null) {
            StructuredLog.record(TAG, "要使用的道具不存在！")
            return false
        }
        return try {
            val propId = prop.getJSONArray("propIdList").getString(0)
            val config = prop.getJSONObject("propConfigVO")
            val propType = config.getString("propType")
            val propName = config.getString("propName")
            val holdsNum = prop.optInt("holdsNum")
            onPropName(propName)
            val renewable = isRenewable(propType)
            StructuredLog.record(TAG, "道具 $propName (类型: $propType), 是否可续用: $renewable, 当前持有数量: $holdsNum")
            val group = ForestPropGroup.forType(propType)
            val result = if (renewable) {
                val checked = ForestPropRpc.consume(group, propId, propType, false).toJsonObject()
                val data = checked.optJSONObject("resData") ?: checked
                when (data.optString("usePropStatus")) {
                    "NEED_CONFIRM_CAN_PROLONG", "REPLACE" -> {
                        StructuredLog.record(TAG, "${propName}需要二次确认，发送确认请求...")
                        GlobalThreadPools.sleepCompat(2000)
                        ForestPropRpc.consume(group, propId, propType, true).toJsonObject().also {
                            val usedName = it.optJSONObject("userPropVO")?.optString("propName") ?: propName
                            StructuredLog.record(TAG, "已使用$usedName")
                        }
                    }
                    else -> checked
                }
            } else {
                ForestPropRpc.consume(group, propId, propType, null).toJsonObject().also {
                    val usedName = it.optJSONObject("userPropVO")?.optString("propName") ?: propName
                    StructuredLog.record(TAG, "已使用$usedName")
                }
            }
            if (ResChecker.checkRes(TAG + "使用道具失败:", result)) {
                if (needRefreshHome) refreshHome()
                true
            } else {
                val data = result.optJSONObject("resData") ?: result
                val desc = data.optString("resultDesc", "未知错误")
                StructuredLog.record("使用道具失败: $desc")
                Toast.show(desc)
                false
            }
        } catch (t: Throwable) {
            onError("usePropBag", t)
            false
        }
    }

    private fun isRenewable(propType: String): Boolean =
        propType.contains("SHIELD") ||
            propType.contains("BOMB_CARD") ||
            propType.contains("DOUBLE_CLICK")

    private companion object {
        const val TAG = "ForestPropService"
    }
}
