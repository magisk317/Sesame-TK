package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.util.FansirsqiUtil
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.ListUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自定义设置管理类
 * 负责“每日单次运行”功能的业务状态与配置持久化。
 *
 * Android UI 由 Compose 层负责，这里不持有 Context 或弹窗实现。
 */
object CustomSettings {
    private const val TAG = "CustomSettings"

    val onlyOnceDaily = BooleanModelField("onlyOnceDaily", "选中每日只运行一次的模块", false)
    val autoHandleOnceDaily = BooleanModelField("autoHandleOnceDaily", "定时自动关闭单次运行", false)

    val autoHandleOnceDailyTimes = ListJoinCommaToStringModelField(
        "autoHandleOnceDailyTimes",
        "自动全量时间点",
        ListUtil.newArrayList("0600", "2000")
    )

    val onlyOnceDailyList = SelectModelField(
        "onlyOnceDailyList",
        "每日只运行一次 | 模块选择",
        LinkedHashSet<String>().apply {
            add("antOrchard")
            add("antCooperate")
            add("antSports")
            add("antMember")
            add("EcoProtection")
            add("greenFinance")
            add("reserve")
        },
        getModuleList()
    )

    private fun getModuleList(): List<MapperEntity> {
        return listOf(
            SimpleEntity("antForest", "蚂蚁森林"),
            SimpleEntity("antFarm", "蚂蚁庄园"),
            SimpleEntity("antOcean", "海洋"),
            SimpleEntity("antOrchard", "农场"),
            SimpleEntity("antStall", "新村"),
            SimpleEntity("antDodo", "神奇物种"),
            SimpleEntity("antCooperate", "蚂蚁森林合种"),
            SimpleEntity("antSports", "运动"),
            SimpleEntity("antMember", "会员"),
            SimpleEntity("travelRights", "出行权益"),
            SimpleEntity("EcoProtection", "生态保护"),
            SimpleEntity("greenFinance", "绿色经营"),
            SimpleEntity("reserve", "保护地"),
            SimpleEntity("other", "其他任务")
        )
    }

    data class Account(
        val userId: String,
        val displayName: String,
    )

    fun getAccounts(): List<Account> {
        val uids = FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
        val backupUid = UserMap.currentUid
        return try {
            uids.map { uid ->
                val displayName = runCatching {
                    UserMap.loadSelf(uid)
                    UserMap.get(uid)?.showName
                }.getOrNull()
                Account(uid, displayName?.takeIf(String::isNotBlank) ?: uid)
            }
        } finally {
            if (backupUid.isNullOrEmpty()) {
                UserMap.unload()
            } else {
                UserMap.loadSelf(backupUid)
            }
        }
    }

    fun getOnceDailyModules(): List<MapperEntity> = onlyOnceDailyList.getExpandValue().orEmpty()

    private fun resetToDefault() {
        onlyOnceDaily.setObjectValue(false)
        autoHandleOnceDaily.setObjectValue(false)
        autoHandleOnceDailyTimes.setObjectValue(ListUtil.newArrayList("0600", "2000"))
        val defaultSet = LinkedHashSet<String>().apply {
            add("antOrchard")
            add("antCooperate")
            add("antSports")
            add("antMember")
            add("EcoProtection")
            add("greenFinance")
            add("reserve")
        }
        onlyOnceDailyList.setObjectValue(defaultSet)
    }

    @JvmStatic
    fun save(userId: String) {
        if (userId.isEmpty()) return
        save(userId, snapshot())
    }

    fun save(userId: String, settings: CustomSettingsDto): Boolean {
        if (userId.isEmpty()) return false
        return try {
            val file = Files.getCustomSetFile(userId) ?: return false
            Files.write2File(CustomSettingsCodec.encode(settings), file)
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "Failed to save custom settings", e)
            false
        }
    }

    @JvmStatic
    fun load(userId: String) {
        if (userId.isEmpty()) return
        try {
            apply(read(userId))
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "Failed to load custom settings", e)
            resetToDefault()
        }
    }

    fun read(userId: String): CustomSettingsDto {
        if (userId.isEmpty()) return CustomSettingsDto()
        return try {
            val file = Files.getCustomSetFile(userId) ?: return CustomSettingsDto()
            val json = Files.readFromFile(file)
            if (json.isBlank()) CustomSettingsDto() else CustomSettingsCodec.decode(json)
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "Failed to read custom settings", e)
            CustomSettingsDto()
        }
    }

    private fun snapshot(): CustomSettingsDto = CustomSettingsDto(
        onlyOnceDaily = onlyOnceDaily.value,
        onlyOnceDailyList = LinkedHashSet(onlyOnceDailyList.value.filterNotNull()),
        autoHandleOnceDaily = autoHandleOnceDaily.value,
        autoHandleOnceDailyTimes = autoHandleOnceDailyTimes.value.filterNotNull(),
    )

    private fun apply(settings: CustomSettingsDto) {
        onlyOnceDaily.setObjectValue(settings.onlyOnceDaily)
        onlyOnceDailyList.setObjectValue(settings.onlyOnceDailyList)
        autoHandleOnceDaily.setObjectValue(settings.autoHandleOnceDaily)
        autoHandleOnceDailyTimes.setObjectValue(settings.autoHandleOnceDailyTimes)
    }

    fun loadForTaskRunner() {
        val currentUid = UserMap.currentUid
        if (!currentUid.isNullOrEmpty()) load(currentUid)
    }

    fun getModuleId(taskInfo: String?): String? {
        if (taskInfo == null) return null
        return when {
            taskInfo.contains("合种") || taskInfo.contains("antCooperate") -> "antCooperate"
            taskInfo.contains("蚂蚁森林") || taskInfo.contains("antForest") -> "antForest"
            taskInfo.contains("蚂蚁庄园") || taskInfo.contains("antFarm") -> "antFarm"
            taskInfo.contains("海洋") || taskInfo.contains("antOcean") -> "antOcean"
            taskInfo.contains("农场") || taskInfo.contains("antOrchard") -> "antOrchard"
            taskInfo.contains("新村") || taskInfo.contains("antStall") -> "antStall"
            taskInfo.contains("神奇物种") || taskInfo.contains("antDodo") -> "antDodo"
            taskInfo.contains("运动") || taskInfo.contains("antSports") -> "antSports"
            taskInfo.contains("会员") || taskInfo.contains("antMember") -> "antMember"
            taskInfo.contains("出行权益") || taskInfo.contains("travelRights") -> "travelRights"
            taskInfo.contains("生态保护") || taskInfo.contains("EcoProtection") -> "EcoProtection"
            taskInfo.contains("绿色经营") || taskInfo.contains("greenFinance") -> "greenFinance"
            taskInfo.contains("保护地") || taskInfo.contains("reserve") -> "reserve"
            taskInfo.contains("其他任务") || taskInfo.contains("other") -> "other"
            else -> null
        }
    }

    fun isOnceDailyBlackListed(taskInfo: String?, status: OnceDailyStatus? = null): Boolean {
        val s = status ?: getOnceDailyStatus(false)
        // 只有当单次运行模式生效，且今日已经完成过首轮全量运行的情况下，才执行黑名单排除
        if (s.isEnabledOverride && s.isFinishedToday) {
            val moduleId = getModuleId(taskInfo)
            if (moduleId != null) {
                return onlyOnceDailyList.value.contains(moduleId)
            }
        }
        return false
    }

    data class OnceDailyStatus(
        val isEnabledOverride: Boolean,
        val isFinishedToday: Boolean
    )

    @JvmStatic
    fun getOnceDailyStatus(enableLog: Boolean = false): OnceDailyStatus {
        val configEnabled = onlyOnceDaily.value == true
        val isFinished = try {
            Status.hasFlagToday("OnceDaily::Finished")
        } catch (e: Throwable) {
            false
        }

        val now = System.currentTimeMillis()
        val interval = BaseModel.checkInterval.value.toLong()
        val isSpecialTime = autoHandleOnceDailyTimes.value.any { timeStr ->
            val startCal = TimeUtil.getTodayCalendarByTimeStr(timeStr)
            if (startCal != null) {
                val startTime = startCal.timeInMillis
                val endTime = startTime + interval
                now in startTime..endTime
            } else {
                false
            }
        }

        var isEnabled = configEnabled

        if (isSpecialTime && autoHandleOnceDaily.value) {
            isEnabled = false
            if (enableLog) StructuredLog.record("自动单次运行触发: 现在处于自动全量运行时段，本次将运行所有已开启的任务")
        } else if (enableLog && autoHandleOnceDaily.value) {
            val sdf = SimpleDateFormat("HHmm", Locale.getDefault())
            val ranges = autoHandleOnceDailyTimes.value.mapNotNull { timeStr ->
                TimeUtil.getTodayCalendarByTimeStr(timeStr)?.let {
                    val endTime = it.timeInMillis + interval
                    "$timeStr-${sdf.format(Date(endTime))}"
                }
            }.joinToString(", ")
            StructuredLog.record("已设置自动全量运行，时段为：$ranges")
        }

        // 如果今日尚未完成首次全量运行，则不启用“跳过”拦截逻辑
        if (isEnabled && !isFinished) {
            isEnabled = false
            if (enableLog) StructuredLog.record("当日单次运行模式生效: 今日尚未完成首次全量运行，本次将运行所有任务")
        } else if (isEnabled) {
            if (enableLog) StructuredLog.record("当日单次运行模式生效: 今日已完成全量运行，已启用跳过黑名单任务")
        }

        return OnceDailyStatus(isEnabled, isFinished)
    }
}

enum class OnceDailyMode(
    val onlyOnceDaily: Boolean,
    val autoHandleOnceDaily: Boolean,
) {
    DISABLED(onlyOnceDaily = false, autoHandleOnceDaily = false),
    ENABLED(onlyOnceDaily = true, autoHandleOnceDaily = false),
    AUTOMATIC(onlyOnceDaily = true, autoHandleOnceDaily = true),
    ;

    companion object {
        fun from(settings: CustomSettingsDto): OnceDailyMode = when {
            !settings.onlyOnceDaily -> DISABLED
            settings.autoHandleOnceDaily -> AUTOMATIC
            else -> ENABLED
        }
    }
}

object OnceDailyTimeInput {
    fun parse(raw: String): List<String>? {
        if (raw.isBlank()) return emptyList()
        val values = raw.split(',').map(String::trim)
        if (values.any(String::isEmpty)) return null
        return values.map { value ->
            if (!value.matches(Regex("\\d{1,4}"))) return null
            val normalized = value.padStart(4, '0')
            val hour = normalized.substring(0, 2).toInt()
            val minute = normalized.substring(2, 4).toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            normalized
        }
    }

    fun validationError(raw: String): String? =
        if (parse(raw) == null) "请输入 0000–2359 的时间点，多个用逗号隔开" else null
}

private class SimpleEntity(id: String, name: String) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }
}
