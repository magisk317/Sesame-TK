package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import java.util.Calendar

/** Display entity backed by the friend-watch repository. */
class FriendWatch(
    id: String,
    name: String,
) : MapperEntity() {
    var startTime: String = ""
    var allGet: Int = 0
    var weekGet: Int = 0

    init {
        this.id = id
        this.name = name
    }

    override fun compareTo(other: MapperEntity): Int {
        val another = other as FriendWatch
        return when {
            weekGet > another.weekGet -> -1
            weekGet < another.weekGet -> 1
            else -> super.compareTo(other)
        }
    }

    companion object {
        private const val TAG = "FriendWatch"
        private val records = LinkedHashMap<String, FriendWatchRecord>()

        @JvmStatic
        @Synchronized
        fun friendWatch(id: String, collectedEnergy: Int) {
            val current = records[id] ?: FriendWatchRecord(
                name = UserMap.getMaskName(id).orEmpty(),
                startTime = TimeUtil.getDateStr(),
            )
            records[id] = current.copy(weekGet = current.weekGet + collectedEnergy)
        }

        @JvmStatic
        @Synchronized
        fun save(userId: String?) {
            if (!FriendWatchRepository.save(userId, records)) {
                StructuredLog.error(TAG, "Failed to save friend-watch records")
            }
        }

        @JvmStatic
        @Synchronized
        fun updateDay(userId: String?) {
            if (userId.isNullOrEmpty()) return
            val file = Files.getFriendWatchFile(userId) ?: return
            if (!needUpdateAll(file.lastModified())) return
            val date = TimeUtil.getDateStr()
            records.replaceAll { _, record ->
                record.copy(
                    allGet = record.allGet + record.weekGet,
                    weekGet = 0,
                    startTime = record.startTime.ifEmpty { date },
                )
            }
            save(userId)
        }

        @JvmStatic
        @Synchronized
        fun load(userId: String?): Boolean {
            records.clear()
            records.putAll(FriendWatchRepository.load(userId))
            return !userId.isNullOrEmpty()
        }

        @JvmStatic
        @Synchronized
        fun unload() {
            records.clear()
        }

        @JvmStatic
        fun needUpdateAll(last: Long): Boolean {
            if (last == 0L) return true
            val previous = Calendar.getInstance().apply { timeInMillis = last }
            val now = Calendar.getInstance()
            if (previous.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return false
            return now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        }

        @JvmStatic
        fun getList(userId: String?): List<FriendWatch> = buildList {
            FriendWatchRepository.load(userId).forEach { (id, record) ->
                val display = FriendWatch(id, record.name).apply {
                    startTime = record.startTime.ifEmpty { "无" }
                    weekGet = record.weekGet
                    allGet = record.allGet + weekGet
                    name = "$name(开始统计时间:$startTime)\n\n周收:$weekGet 总收:$allGet"
                }
                add(display)
            }
        }
    }
}
