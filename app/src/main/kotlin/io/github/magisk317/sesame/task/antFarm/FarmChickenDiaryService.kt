package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Owns the chicken-diary interaction and historical diary collection workflow. */
class FarmChickenDiaryService {
    private val tag = "FarmChickenDiaryService"
    private companion object {
        const val MODE_ONCE = 0
        const val MODE_MONTH = 1
        const val MODE_ALL = 2
    }

        private fun diaryTietze(@Suppress("SameParameterValue") queryDayStr: String?) {
            val diaryDateStr: String?
            try {
                var jo = FarmSocialRpc.queryChickenDiary(queryDayStr).toJsonObject()
                if (ResChecker.checkRes(tag, jo)) {
                    val data = jo.getJSONObject("data")
                    val chickenDiary = data.getJSONObject("chickenDiary")
                    diaryDateStr = chickenDiary.optString("diaryDateStr")
                    if (data.has("hasTietie")) {
                        if (!data.optBoolean("hasTietie", true)) {
                            jo = FarmSocialRpc.diaryTietie(diaryDateStr, "NEW").toJsonObject()
                            if (ResChecker.checkRes(tag, jo)) {
                                val prizeType = jo.optString("prizeType")
                                val prizeNum = jo.optInt("prizeNum", 0)
                                StructuredLog.farm("[$diaryDateStr]贴贴小鸡💞[$prizeType*$prizeNum]")
                            } else {
                                StructuredLog.record(tag, "贴贴小鸡失败:")
                                StructuredLog.record(jo.optString("memo"), jo.toString())
                            }
                            if (!chickenDiary.has("statisticsList")) return
                            val statisticsList = chickenDiary.getJSONArray("statisticsList")
                            if (statisticsList.length() > 0) {
                                for (i in 0..<statisticsList.length()) {
                                    val tietieStatus = statisticsList.getJSONObject(i)
                                    val tietieRoleId = tietieStatus.optString("tietieRoleId")
                                    jo = FarmSocialRpc.diaryTietie(
                                            diaryDateStr,
                                            tietieRoleId
                                        ).toJsonObject()
                                    if (ResChecker.checkRes(tag, jo)) {
                                        val prizeType = jo.optString("prizeType")
                                        val prizeNum = jo.optInt("prizeNum", 0)
                                        StructuredLog.farm("[$diaryDateStr]贴贴小鸡💞[$prizeType*$prizeNum]")
                                    } else {
                                        StructuredLog.record(tag, "贴贴小鸡失败:")
                                        StructuredLog.record(jo.optString("memo"), jo.toString())
                                    }
                                }
                            }
                        }
                    }
                } else {
                    StructuredLog.record(tag, "贴贴小鸡-获取小鸡日记详情 err:")
                    StructuredLog.record(jo.optString("resultDesc"), jo.toString())
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(tag, "queryChickenDiary err:",t)
            }
        }

        /**
         * 点赞小鸡日记
         *
         */
        private fun collectDiary(queryDayStr: String?): String? {
            var diaryDateStr: String? = null
            try {
                var jo = FarmSocialRpc.queryChickenDiary(queryDayStr).toJsonObject()
                if (ResChecker.checkRes(tag, jo)) {
                    val data = jo.getJSONObject("data")
                    val chickenDiary = data.getJSONObject("chickenDiary")
                    diaryDateStr = chickenDiary.optString("diaryDateStr")
                    // 点赞小鸡日记
                    if (!chickenDiary.optBoolean("collectStatus", true)) {
                        val diaryId = chickenDiary.optString("diaryId")
                        jo = FarmSocialRpc.collectChickenDiary(diaryId).toJsonObject()
                        if (jo.optBoolean("success", true)) {
                            StructuredLog.farm("[$diaryDateStr]点赞小鸡日记💞成功")
                        }
                    }
                } else {
                    StructuredLog.record(tag, "日记点赞-获取小鸡日记详情 err:")
                    StructuredLog.record(jo.optString("resultDesc"), jo.toString())
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(tag, "queryChickenDiary err:",t)
            }
            return diaryDateStr
        }

        private suspend fun queryChickenDiaryList(
            queryMonthStr: String?,
            `fun`: (String?) -> String?
        ): Boolean {
            var hasPreviousMore = false
            try {
                var jo: JsonObject?
                jo = if (StringUtil.isEmpty(queryMonthStr)) {
                    FarmSocialRpc.queryChickenDiaryList().toJsonObject()
                } else {
                    FarmSocialRpc.queryChickenDiaryList(queryMonthStr).toJsonObject()
                }
                if (ResChecker.checkRes(tag, jo)) {
                    jo = jo.getJSONObject("data")
                    hasPreviousMore = jo.optBoolean("hasPreviousMore", false)
                    val chickenDiaryBriefList = jo.optJSONArray("chickenDiaryBriefList")
                    if (chickenDiaryBriefList != null && chickenDiaryBriefList.length() > 0) {
                        for (i in chickenDiaryBriefList.length() - 1 downTo 0) {
                            jo = chickenDiaryBriefList.getJSONObject(i)
                            if (!jo.optBoolean("read", true) ||
                                !jo.optBoolean("collectStatus")
                            ) {
                                val dateStr = jo.optString("dateStr")
                                `fun`(dateStr)
                                delay(300)
                            }
                        }
                    }
                } else {
                    StructuredLog.record(jo.optString("resultDesc"), jo.toString())
                }
            } catch (e: CancellationException) {
                // 协程取消异常必须重新抛出，不能吞掉
                 StructuredLog.record(tag, "queryChickenDiaryList 协程被取消")
                throw e
            } catch (t: Throwable) {
                hasPreviousMore = false
                StructuredLog.printStackTrace(tag, "queryChickenDiaryList err:",t)
            }
            return hasPreviousMore
        }

        suspend fun run(tieDiary: Boolean, collectMode: Int) {
            if (tieDiary) { // 贴贴小鸡
                diaryTietze("")
            }

            // 小鸡日记点赞
            var dateStr: String? = null
            var yearMonth = YearMonth.now()
            var previous = false
            try {
                if (collectMode >= MODE_ONCE) {
                    delay(300)
                    dateStr = collectDiary("")
                }
                if (collectMode >= MODE_MONTH) {
                    if (dateStr == null) {
                        StructuredLog.error(tag, "小鸡日记点赞-dateStr为空，使用当前日期")
                    } else {
                        yearMonth = YearMonth.from(LocalDate.parse(dateStr))
                    }
                    delay(300)
                    previous = queryChickenDiaryList(
                        yearMonth.toString()
                    ) { queryDayStr ->
                        collectDiary(queryDayStr)
                    }
                }
                if (collectMode >= MODE_ALL) {
                    while (previous) {
                        delay(300)
                        yearMonth = yearMonth.minusMonths(1)
                        previous = queryChickenDiaryList(
                            yearMonth.toString()
                        ) { queryDayStr ->
                            this.collectDiary(queryDayStr)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // 协程取消异常必须重新抛出，不能吞掉
                 StructuredLog.record(tag, "doChickenDiary 协程被取消")
                throw e
            } catch (e: Exception) {
                StructuredLog.printStackTrace(tag, "doChickenDiary err:",e)
            }
        }


}
