package io.github.magisk317.sesame.task.other.credit2101

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject

/**
 * 信用2101 账户资产查询与解析（Phase 3.1 *Service 抽取，仿 [io.github.magisk317.sesame.task.antFarm.FarmAccelerateService] 模板）。
 *
 * 把原散落在 [Credit2101] 的 queryAccountAsset 解析逻辑抽成纯协作者：RPC 调用经构造注入，
 * 解析为纯函数，便于单测注入 fake 原始响应断言字段映射。
 */
internal class CreditAccountService(
    private val tag: () -> String,
    private val queryRaw: () -> String,
    private val logError: (Throwable) -> Unit = { StructuredLog.printStackTrace(tag(), it) },
) {
    /** 账户资产快照。 */
    data class AccountInfo(
        val creditProfile: Int,
        val creditSpValue: Int,
        val exploreStamina: Int,
        val energyStamina: Int,
        val lotteryNo: Int,
        val cityCode: String?,
        val cityName: String?,
    )

    /** 拉取并解析账户资产；RPC 失败或解析异常返回 null。 */
    fun query(): AccountInfo? {
        val resp = queryRaw()
        if (!ResChecker.checkRes(tag(), resp)) return null

        return runCatching {
            val jo = resp.toJsonObject()
            if (!ResChecker.checkRes(tag(), jo)) return null

            val accountAssetVO = jo.optJSONObject("accountAssetVO")
            val exploreStaminaVO = jo.optJSONObject("exploreStaminaVO")
            val energyStaminaVO = jo.optJSONObject("energyStaminaVO")
            val accountVO = jo.optJSONObject("accountVO")

            AccountInfo(
                creditProfile = accountAssetVO?.optInt("creditProfile", 0) ?: 0,
                creditSpValue = accountAssetVO?.optInt("creditSpValue", 0) ?: 0,
                exploreStamina = exploreStaminaVO?.optInt("staminaAvailable", 0) ?: 0,
                energyStamina = energyStaminaVO?.optInt("staminaAvailable", 0) ?: 0,
                lotteryNo = jo.optInt("lotteryNo", 0),
                cityCode = accountVO?.optString("cityCode", "null"),
                cityName = accountVO?.optString("cityName", "null"),
            )
        }.getOrElse {
            logError(it)
            null
        }
    }
}
