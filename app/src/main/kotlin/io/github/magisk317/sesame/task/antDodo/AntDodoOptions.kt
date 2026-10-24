package io.github.magisk317.sesame.task.antDodo

/** Shared Dodo configuration values kept outside the task implementation. */
object AntDodoOptions {
    const val COLLECT_ANIMAL = "COLLECT_ANIMAL"
    const val ADD_COLLECT_TO_FRIEND_LIMIT = "ADD_COLLECT_TO_FRIEND_LIMIT"
    const val UNIVERSAL_CARD = "UNIVERSAL_CARD"

    const val COLLECT = 0
    const val DONT_COLLECT = 1
    @JvmField val collectToFriendNames = arrayOf("选中帮抽卡", "选中不帮抽卡")

    const val ALL_COLLECTION = 0
    const val EXCLUDE_CURRENT = 1
    const val PRIORITY_MAX_PROGRESS = 2
    @JvmField val universalCardNames = arrayOf("所有图鉴", "除当前图鉴", "优先合成进度最高")
}
