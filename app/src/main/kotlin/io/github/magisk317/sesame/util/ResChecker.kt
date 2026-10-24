package io.github.magisk317.sesame.util

import kotlinx.serialization.json.JsonObject

/** Applies the shared success and ignorable-error policy to RPC responses. */
object ResChecker {
    private val ignoreKeywords = listOf(
        "当前参与人数过多", "请稍后再试", "手速太快", "频繁", "操作过于频繁",
        "我的小鸡在睡觉中", "小鸡在睡觉", "无法操作", "有人抢在你", "饲料槽已满",
        "当日达到上限", "适可而止", "不支持rpc完成的任务", "不支持rpc调用",
        "任务全局配置不存在", "庄园的小鸡太多了", "同一好友新村，只能摆一个小摊哦",
        "今日助力次数已用完", "收摊成功", "任务已完成",
        "今日已抽过签", "盖帽值拦截", "路线无效", "开店1", "参数缺失",
        "系统出错，正在排查", "系统繁忙，请稍后重试", "任务已完结",
        "捐赠的是自营项目但是没有指定标的物",
    )

    @JvmStatic
    fun checkRes(tag: String, response: JsonObject): Boolean {
        if (response.isEmpty()) return false  // 空响应（服务端返回 null），静默跳过
        if (response.optBoolean("success") || response.optBoolean("isSuccess")) return true
        val resultCode = response.optString("resultCode")
        if (resultCode.equals("SUCCESS", true) || resultCode == "100" || resultCode == "200") return true
        if (response.optString("memo").equals("SUCCESS", true)) return true
        val text = listOf(response.optString("resultDesc"), response.optString("memo"), response.optString("desc"), response.optString("errorMessage"))
        if (ignoreKeywords.any { keyword -> text.any { it.contains(keyword) } }) return false
        if (resultCode in setOf("I07", "ILLEGAL_ARGUMENT", "I09")) return false
        StructuredLog.error(tag, "Check failed: $response")
        return false
    }

    @JvmStatic
    fun checkRes(tag: String, json: String): Boolean = checkRes(tag, json.toJsonObject())

}
