package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.util.PhotoCache
import io.github.magisk317.sesame.util.PhotoCacheEntry
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.model.ModelRegistry
import kotlinx.serialization.json.JsonArray
import java.util.regex.Pattern

object EcoLife {
    val TAG: String = EcoLife::class.java.getSimpleName()

    /**
     * 执行绿色行动任务，包括查询任务开通状态、开通绿色任务、执行打卡任务等操作。
     * 1. 调用接口查询绿色行动的首页数据，检查是否成功。
     * 2. 如果绿色任务尚未开通，且用户未开通绿色任务，则记录日志并返回。
     * 3. 如果绿色任务尚未开通，且用户已开通绿色任务，则尝试开通绿色任务。
     * 4. 开通绿色任务成功后，再次查询任务状态，并更新数据。
     * 5. 获取任务的日期标识和任务列表，执行打卡任务。
     * 6. 如果绿色打卡设置为启用，执行 `ecoLifeTick` 方法提交打卡任务。
     * 7. 如果光盘打卡设置为启用，执行 `photoGuangPan` 方法上传光盘照片。
     * 8. 异常发生时，记录错误信息并打印堆栈。
     */
    fun ecoLife() {
        try {
            val antForest = ModelRegistry.getModel(AntForest::class.java) ?: return
            // 查询首页信息
            var jsonObject = ForestEcoLifeRpc.queryHome().toJsonObject()
            if (!jsonObject.optBoolean("success")) {
                StructuredLog.record("$TAG.ecoLife.queryHomePage", jsonObject.optString("resultDesc"))
                return
            }
            var data = jsonObject.getJSONObject("data")


            // 获取当天的积分和任务列表
            var dayPoint = data.optString("dayPoint", "0")
            if (dayPoint == "0") {
                StructuredLog.error(TAG, "不知道什么B原因自己去绿色行动找")
                return
            }

            if (antForest.ecoLifeOption?.value?.contains("plate") == true) {
                // 光盘行动
                photoGuangPan(dayPoint)
            }

            val actionListVO = data.getJSONArray("actionListVO")
            // 绿色打卡
            if (antForest.ecoLifeOption?.value?.contains("tick") == true) {
                if (!data.getBoolean("openStatus")) {
                    if (!openEcoLife() || antForest.ecoLifeOpen?.value != true) {
                        return
                    }
                    jsonObject = ForestEcoLifeRpc.queryHome().toJsonObject()
                    data = jsonObject.getJSONObject("data")
                    dayPoint = data.getString("dayPoint")
                }
                ecoLifeTick(actionListVO, dayPoint)
            }
        } catch (th: Throwable) {
            StructuredLog.record(TAG, "ecoLife err:")
            StructuredLog.printStackTrace(TAG, th)
        }
    }

    /**
     * 封装绿色任务开通的逻辑
     *
     * @return 是否成功开通绿色任务
     */
    fun openEcoLife(): Boolean {
        val jsonObject = ForestEcoLifeRpc.open().toJsonObject()
        if (!jsonObject.optBoolean("success")) {
            StructuredLog.record("$TAG.ecoLife.openEcolife", jsonObject.optString("resultDesc"))
            return false
        }
        val opResult = jsonObject.optJSONObject("data")?.optString("opResult")
        if ("true" != opResult) {
            return false
        }
        StructuredLog.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了")
        return true
    }

    /**
     * 执行绿色行动打卡任务，遍历任务列表，依次提交每个未完成的任务。
     * 1. 遍历给定的任务列表（`actionListVO`），每个任务项包含多个子任务。
     * 2. 对于每个子任务，检查其是否已完成，如果未完成则提交打卡请求。
     * 3. 特别处理任务 ID 为 "photoguangpan" 的任务，跳过该任务的打卡。
     * 4. 如果任务打卡成功，记录成功日志；否则记录失败原因。
     * 5. 每次打卡请求后，等待 500 毫秒以避免请求过于频繁。
     * 6. 异常发生时，记录详细的错误信息。
     *
     * @param actionListVO 任务列表，每个任务包含多个子任务
     * @param dayPoint     任务的日期标识，用于标识任务的日期
     */
    fun ecoLifeTick(actionListVO: JsonArray, dayPoint: String?) {
        try {
            val source = "source"
            for (i in 0..<actionListVO.length()) {
                val actionVO = actionListVO.getJSONObject(i)
                val actionItemList = actionVO.getJSONArray("actionItemList")
                for (j in 0..<actionItemList.length()) {
                    val actionItem = actionItemList.getJSONObject(j)
                    if (!actionItem.has("actionId")) continue
                    if (actionItem.getBoolean("actionStatus")) continue
                    val actionId = actionItem.getString("actionId")
                    val actionName = actionItem.getString("actionName")
                    if ("photoguangpan" == actionId) continue
                    val jo = ForestEcoLifeRpc.tick(actionId, dayPoint, source).toJsonObject()
                    if (ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.forest("绿色打卡🍀[$actionName]") // 成功打卡日志
                    } else {
                        // 记录失败原因
                        StructuredLog.error(TAG + jo.getString("resultDesc"))
                        StructuredLog.error(TAG + jo)
                    }
                }
            }
        } catch (th: Throwable) {
            StructuredLog.record(TAG, "ecoLifeTick err:")
            StructuredLog.printStackTrace(TAG, th)
        }
    }

    /**
     * 执行光盘行动任务，上传餐前餐后照片并提交任务。
     * 1. 查询当前任务的状态。
     * 2. 如果任务未完成，检查是否已有餐前餐后照片的URL，如果没有则从接口获取并保存。
     * 3. 上传餐前餐后照片，上传成功后提交任务，标记任务为完成。
     * 4. 如果任务已完成，则不做任何操作。
     * 5. 如果遇到任何错误，记录错误信息并停止执行。
     *
     * @param dayPoint 任务的日期标识，用于标识任务的日期
     */
    fun photoGuangPan(dayPoint: String?) {
        try {
            if (Status.hasFlagToday("EcoLife::photoGuangPan")) return

            val source = "renwuGD" // 任务来源标识

            val allPhotos = PhotoCache.read().toMutableList()
            StructuredLog.record("$TAG [DEBUG] guangPanPhoto 数据内容: $allPhotos")
            // 查询今日任务状态
            var str = ForestEcoLifeRpc.queryDish(source, dayPoint)
            var jo = str.toJsonObject()
            // 如果请求失败，则记录错误信息并返回
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.record("$TAG.photoGuangPan.ecolifeQueryDish", jo.optString("resultDesc"))
                return
            }
            var photo: PhotoCacheEntry? = null
            val data = jo.optJSONObject("data")
            if (data != null) {
                val beforeMealsImageUrl = data.optString("beforeMealsImageUrl")
                val afterMealsImageUrl = data.optString("afterMealsImageUrl")
                // 如果餐前和餐后照片URL都存在，进行提取
                if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(
                        afterMealsImageUrl
                    )
                ) {
                    // 使用正则从URL中提取照片的路径部分
                    val pattern = Pattern.compile("img/(.*)/original")
                    val beforeMatcher = pattern.matcher(beforeMealsImageUrl)
                    val afterMatcher = pattern.matcher(afterMealsImageUrl)
                    if (beforeMatcher.find() && afterMatcher.find()) {
                        val candidate = PhotoCacheEntry(
                            before = beforeMatcher.group(1).orEmpty(),
                            after = afterMatcher.group(1).orEmpty(),
                        )
                        if (candidate.before.isNotBlank() && candidate.after.isNotBlank() && candidate !in allPhotos) {
                            allPhotos.add(candidate)
                            if (!PhotoCache.write(allPhotos)) {
                                StructuredLog.error(TAG, "光盘行动照片缓存写入失败")
                            }
                        }
                    }
                }
            }
            if ("SUCCESS" == jo.optJSONObject("data")?.optString("status")) {
                return
            }
            if (allPhotos.isEmpty()) {
                if (!Status.hasFlagToday("EcoLife::plateNotify0")) {
                    StructuredLog.forest("光盘行动🍛缓存中没有照片数据")
                    Status.setFlagToday("EcoLife::plateNotify0")
                }
                photo = null
            } else {
                photo = allPhotos[RandomUtil.nextInt(0, allPhotos.size)]
            }
            if (photo == null) {
                if (!Status.hasFlagToday("EcoLife::plateNotify1")) {
                    StructuredLog.forest("光盘行动🍛请先完成一次光盘打卡")
                    Status.setFlagToday("EcoLife::plateNotify1")
                }
                return
            }
            str = ForestEcoLifeRpc.uploadDishImage(
                "BEFORE_MEALS",
                photo.before,
                0.16571736,
                0.07448776,
                0.7597949,
                dayPoint
            )
            jo = str.toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            str = ForestEcoLifeRpc.uploadDishImage(
                "AFTER_MEALS",
                photo.after,
                0.00040030346,
                0.99891376,
                0.0006858421,
                dayPoint
            )
            jo = str.toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            // 提交任务
            str = ForestEcoLifeRpc.tick("photoguangpan", dayPoint, source)
            jo = str.toJsonObject()
            // 如果提交失败，记录错误信息并返回
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            // 任务完成，输出完成日志
            val toastMsg = "光盘行动🍛任务完成#" + jo.getJSONObject("data").getString("toastMsg")
            Status.setFlagToday("EcoLife::photoGuangPan")
            StructuredLog.forest(toastMsg)
            Toast.show(toastMsg)
        } catch (t: Throwable) {
            // 捕获异常，记录错误信息和堆栈追踪
            StructuredLog.record(TAG, "photoGuangPan err:")
            StructuredLog.printStackTrace(TAG, t)
        }
    }
}
