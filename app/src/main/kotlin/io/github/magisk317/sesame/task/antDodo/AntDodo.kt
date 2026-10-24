package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.entity.OtherEntityProvider
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/** Dodo model orchestration. RPC and task planning live in dedicated gateways/services. */
class AntDodo : ModelTask() {
    private lateinit var collectToFriendType: ChoiceModelField
    private lateinit var collectToFriendList: SelectModelField
    private lateinit var usePropGroup: SelectModelField
    private lateinit var universalCardStrategy: ChoiceModelField
    private lateinit var autoGenerateBook: BooleanModelField

    override fun getName() = "神奇物种"
    override fun getGroup() = ModelGroup.FOREST
    override fun getIcon() = "AntDodo.png"

    override fun getFields(): ModelFields = ModelFields().apply {
        addField(ChoiceModelField("collectToFriendType", "帮抽卡 | 动作", AntDodoOptions.DONT_COLLECT, AntDodoOptions.collectToFriendNames).also { collectToFriendType = it })
        addField(SelectModelField("collectToFriendList", "帮抽卡 | 好友列表", emptySet(), AlipayUser::getList).also { collectToFriendList = it })
        addField(SelectModelField("usepropGroup", "使用道具类型", emptySet(), OtherEntityProvider.listPropGroupOptions()).also { usePropGroup = it })
        addField(ChoiceModelField("usePropUNIVERSALCARDType", "万能卡 | 使用方式", AntDodoOptions.EXCLUDE_CURRENT, AntDodoOptions.universalCardNames).also { universalCardStrategy = it })
        addField(BooleanModelField("autoGenerateBook", "自动合成图鉴", false).also { autoGenerateBook = it })
    }

    override fun runJava() {
        try {
            AntDodoTaskRunner.run()
            useProps()
            collectCards()
            // Friend draws and surplus-card exchange are independent of the
            // personal album state. A complete album must not stop either.
            collectToFriendCards()
            if (autoGenerateBook.value) generateBooks()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "start Dodo.run err:", t)
        }
    }

    private fun collectCards() {
        try {
            val response = AntDodoRpcCall.queryAnimalStatus().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.record(TAG, "collect错误${response.optString("resultDesc")}")
                return
            }
            if (response.optJSONObject("data")?.optBoolean("collect") == true) {
                StructuredLog.forest("神奇物种🦕 卡片今日收集完成")
            } else collectAvailableCards()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AntDodo Collect err:", t)
        }
    }

    private fun collectAvailableCards() {
        try {
            val response = AntDodoRpcCall.homePage().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val data = response.getJSONObject("data")
            val book = data.getJSONObject("animalBook")
            val endDate = book.getString("endDate") + " 23:59:59"
            AntDodoTaskRunner.run()
            if (!DodoTimePolicy.isWithinEightDays(endDate) || DodoTimePolicy.isLastDay(endDate)) useProps()

            val limits = data.optJSONArray("limit") ?: return
            val daily = (0 until limits.length()).map { limits.getJSONObject(it) }
                .firstOrNull { it.optString("actionCode") == "DAILY_COLLECT" } ?: return
            repeat(daily.optInt("leftFreeQuota")) {
                val result = AntDodoRpcCall.collect().toJsonObject()
                if (!ResChecker.checkRes(TAG, result)) return@repeat
                val animal = result.getJSONObject("data").getJSONObject("animal")
                StructuredLog.forest("神奇物种🦕[${animal.optString("ecosystem")}]#${animal.optString("name")}")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AntDodo CollectAnimalCard err:", t)
        }
    }

    private fun useProps() {
        try {
            val response = AntDodoRpcCall.propList().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val data = response.optJSONObject("data") ?: return
            val props = data.optJSONArray("propList") ?: return
            val home = runCatching { AntDodoRpcCall.homePage().toJsonObject().optJSONObject("data") }.getOrNull()
            var current = home?.optInt("curCollectionCategoryCount", 0) ?: 0
            val total = home?.optJSONObject("animalBook")?.optInt("totalCount", 0) ?: 0
            var bookFull = total > 0 && current >= total
            val selected = usePropGroup.value.filterNotNull().toSet()

            propLoop@ for (index in 0 until props.length()) {
                val prop = props.getJSONObject(index)
                val group = prop.optJSONObject("propConfig")?.optString("propGroup") ?: continue
                if (prop.optInt("holdsNum") <= 0) continue
                val ids = prop.optJSONArray("propIdList") ?: continue
                when {
                    group == AntDodoOptions.UNIVERSAL_CARD && AntDodoOptions.UNIVERSAL_CARD in selected && !bookFull -> {
                        repeat(ids.length()) {
                            val target = targetAnimalId()
                            if (target.isEmpty()) return@repeat
                            if (ResChecker.checkRes(TAG, AntDodoRpcCall.consumeProp(ids.getString(it), prop.optString("propType"), target))) {
                                current++
                                bookFull = total > 0 && current >= total
                            }
                            GlobalThreadPools.sleepCompat(2000)
                        }
                    }
                    group == AntDodoOptions.ADD_COLLECT_TO_FRIEND_LIMIT && group in selected -> repeat(ids.length()) {
                        if (ResChecker.checkRes(TAG, AntDodoRpcCall.consumePropForFriend(ids.getString(it), prop.optString("propType")))) {
                            StructuredLog.record(TAG, "神奇物种🃏 成功使用[好友抽卡道具]")
                        }
                        GlobalThreadPools.sleepCompat(2000)
                    }
                    group == AntDodoOptions.COLLECT_ANIMAL && group in selected -> for (idIndex in 0 until ids.length()) {
                        if (bookFull) {
                            StructuredLog.record(TAG, "图鉴已集满，自动关停后续抽卡动作")
                            break@propLoop
                        }
                        val result = AntDodoRpcCall.consumeProp(ids.getString(idIndex), prop.optString("propType"), null)
                        if (ResChecker.checkRes(TAG, result)) {
                            val useResult = result.toJsonObject().optJSONObject("data")?.optJSONObject("useResult")
                            val detail = useResult?.optJSONObject("collectDetail")
                            val isNew = detail?.optBoolean("newCard") == true
                            if (isNew) {
                                current++
                                bookFull = total > 0 && current >= total
                            }
                            StructuredLog.forest("使用道具抽卡: ${useResult?.optJSONObject("animal")?.optString("name", "未知物种")} | 进度: $current/$total")
                        }
                        GlobalThreadPools.sleepCompat(2000)
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "propList 处理异常", t)
        }
    }

    private data class CardHolding(
        val animalId: String,
        val name: String,
        val count: Int,
    )

    private fun collectToFriendCards() {
        try {
            val response = AntDodoRpcCall.queryFriend().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val limits = response.getJSONObject("data").getJSONObject("extend").optJSONArray("limit") ?: return
            val limit = (0 until limits.length()).asSequence().map { limits.getJSONObject(it) }
                .firstOrNull { it.optString("actionCode") == "COLLECT_TO_FRIEND" } ?: return
            if (limit.has("startTime") && limit.optLong("startTime") > System.currentTimeMillis()) {
                StructuredLog.record(TAG, "神奇物种帮好友抽卡未到开放时间: ${limit.optString("startTimeStr")}")
                return
            }
            val count = limit.optInt("leftLimit")
            if (count <= 0) return
            val friends = response.getJSONObject("data").optJSONArray("friends") ?: return
            val selectedFriends = collectToFriendList.value.filterNotNull().filter { it != UserMap.currentUid }.toSet()
            var remaining = count
            for (i in 0 until friends.length()) {
                if (remaining <= 0) break
                val friend = friends.getJSONObject(i)
                if (friend.optBoolean("dailyCollect")) continue
                val id = friend.optString("userId")
                // An empty list means all friends. This keeps old configs that
                // used the former "selected friends" default from doing zero work.
                val shouldCollect = if (selectedFriends.isEmpty()) {
                    true
                } else if (collectToFriendType.value == AntDodoOptions.COLLECT) {
                    id in selectedFriends
                } else {
                    id !in selectedFriends
                }
                if (!shouldCollect) continue
                val result = AntDodoRpcCall.collecttarget(id).toJsonObject()
                if (ResChecker.checkRes(TAG, result)) {
                    val animal = result.getJSONObject("data").getJSONObject("animal")
                    val animalId = animal.optString("animalId")
                    val animalName = animal.optString("name", "未知物种")
                    val holdings = ownCardHoldings()
                    val drawnCount = holdings.firstOrNull { it.animalId == animalId }?.count ?: 0
                    val mostHeld = holdings.maxByOrNull { it.count }
                    StructuredLog.forest(
                        "神奇物种🦕帮好友[${UserMap.getMaskName(id)}]抽到[$animalName]，" +
                            "我方已有${drawnCount}张；我方最多[${mostHeld?.name ?: "未知物种"}] ${mostHeld?.count ?: 0}张",
                    )
                    if (mostHeld != null && mostHeld.count > 0) {
                        val exchange = AntDodoRpcCall.social(mostHeld.animalId, id).toJsonObject()
                        if (ResChecker.checkRes(TAG, exchange)) {
                            StructuredLog.forest(
                                "神奇物种🦕已交换给[${UserMap.getMaskName(id)}]：" +
                                    "${mostHeld.name} 1张（交换前${mostHeld.count}张）",
                            )
                        } else {
                            StructuredLog.error(TAG, "神奇物种🦕交换失败：${mostHeld.name} -> ${UserMap.getMaskName(id)}")
                        }
                    } else {
                        StructuredLog.record(TAG, "神奇物种🦕无可交换卡片")
                    }
                    remaining--
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AntDodo CollectHelpFriend err:", t)
        }
    }

    private fun ownCardHoldings(): List<CardHolding> {
        val holdings = mutableListOf<CardHolding>()
        val books = allBookList()
        for (index in 0 until books.length()) {
            val book = books.optJSONObject(index) ?: continue
            val bookId = book.optJSONObject("animalBookResult")?.optString("bookId").orEmpty()
            if (bookId.isEmpty()) continue
            val animals = AntDodoRpcCall.queryBookInfo(bookId).toJsonObject()
                .optJSONObject("data")?.optJSONArray("animalForUserList") ?: continue
            for (animalIndex in 0 until animals.length()) {
                val item = animals.optJSONObject(animalIndex) ?: continue
                val animal = item.optJSONObject("animal") ?: continue
                val animalId = animal.optString("animalId")
                if (animalId.isEmpty()) continue
                holdings += CardHolding(
                    animalId = animalId,
                    name = animal.optString("name", "未知物种"),
                    count = item.optJSONObject("collectDetail")?.optInt("count", 0) ?: 0,
                )
            }
        }
        return holdings
    }

    private fun targetAnimalId(): String {
        val books = allBookList()
        val booksWithIds = (0 until books.length()).mapNotNull { index ->
            val book = books.optJSONObject(index) ?: return@mapNotNull null
            val bookId = book.optJSONObject("animalBookResult")?.optString("bookId").orEmpty()
            bookId.takeIf { it.isNotEmpty() }?.let { it to book }
        }
        if (booksWithIds.isEmpty()) return ""

        val currentBookId = booksWithIds.firstOrNull { (_, book) -> book.optString("bookStatus") == "DOING" }?.first
        val candidates = when (universalCardStrategy.value) {
            AntDodoOptions.EXCLUDE_CURRENT -> booksWithIds.filterNot { (id, _) -> id == currentBookId }
                .ifEmpty { booksWithIds }
            AntDodoOptions.PRIORITY_MAX_PROGRESS -> booksWithIds.maxByOrNull { (_, book) -> bookProgress(book) }
                ?.let(::listOf) ?: booksWithIds
            else -> booksWithIds
        }

        var bestAnimalId = ""
        var bestCount = Int.MAX_VALUE
        for ((bookId, _) in candidates) {
            val animals = AntDodoRpcCall.queryBookInfo(bookId).toJsonObject()
                .optJSONObject("data")?.optJSONArray("animalForUserList") ?: continue
            for (index in 0 until animals.length()) {
                val item = animals.optJSONObject(index) ?: continue
                val animalId = item.optJSONObject("animal")?.optString("animalId").orEmpty()
                if (animalId.isEmpty()) continue
                val count = item.optJSONObject("collectDetail")?.optInt("count", 0) ?: 0
                if (count < bestCount) {
                    bestCount = count
                    bestAnimalId = animalId
                }
            }
        }
        return bestAnimalId
    }

    private fun bookProgress(book: JsonObject): Double {
        val parts = book.optString("collectProgress", "0/0").split("/")
        if (parts.size != 2) return 0.0
        val current = parts[0].toDoubleOrNull() ?: return 0.0
        val total = parts[1].toDoubleOrNull() ?: return 0.0
        return if (total > 0) current / total else 0.0
    }

    private fun allBookList(): JsonArray {
        val all = mutableListOf<kotlinx.serialization.json.JsonElement>()
        var page: String? = null
        while (true) {
            val response = AntDodoRpcCall.queryBookList(64, page).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) break
            val data = response.optJSONObject("data") ?: break
            data.optJSONArray("bookForUserList")?.let { list -> for (i in 0 until list.length()) all.add(list.get(i)) }
            if (!data.optBoolean("hasMore")) break
            page = data.optString("nextPageStart").takeIf { it.isNotEmpty() }
            if (page.isNullOrEmpty()) break
            GlobalThreadPools.sleepCompat(300)
        }
        return JsonArray(all)
    }

    private fun generateBooks() {
        try {
            val books = allBookList()
            for (i in 0 until books.length()) {
                val item = books.getJSONObject(i)
                if (item.optString("medalGenerationStatus") != "CAN_GENERATE") continue
                val result = item.optJSONObject("animalBookResult") ?: continue
                val response = AntDodoRpcCall.generateBookMedal(result.optString("bookId")).toJsonObject()
                if (ResChecker.checkRes(TAG, response)) StructuredLog.forest("神奇物种🦕合成勋章[${result.optString("ecosystem")}]")
                GlobalThreadPools.sleepCompat(300)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "autoGenerateBook err:", t)
        }
    }

    companion object {
        private const val TAG = "AntDodo"

        init {
            ModelOrder.register(ModelDefinition(AntDodo::class.java) { AntDodo() })
        }
    }
}
