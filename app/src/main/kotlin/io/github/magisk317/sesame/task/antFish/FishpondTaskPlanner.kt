package io.github.magisk317.sesame.task.antFish

internal sealed interface FishpondTaskAction {
    data class Sign(val signKey: String) : FishpondTaskAction
    data class Exchange(val taskId: String, val manurePaid: Int, val rodExchangeCount: Int) : FishpondTaskAction
    data class Finish(val sceneCode: String, val taskType: String, val title: String) : FishpondTaskAction
    data class Receive(val sceneCode: String, val taskId: String, val title: String) : FishpondTaskAction
}

internal object FishpondTaskPlanner {
    fun actions(snapshot: FishpondTaskSnapshot, signAttempted: Boolean): List<FishpondTaskAction> = buildList {
        snapshot.todaySign
            ?.takeIf { !it.signed && !signAttempted && it.signKey.isNotBlank() }
            ?.let { add(FishpondTaskAction.Sign(it.signKey)) }

        val exchangeTask = snapshot.tasks.firstOrNull {
            it.actionType == "EXCH_MANURE_4_ROD" && it.taskStatus != "RECEIVED"
        }
        val exchange = snapshot.exchange
        if (exchangeTask != null && exchange != null && exchange.manurePriceOfRod > 0) {
            val affordable = exchange.userManure / exchange.manurePriceOfRod
            val count = minOf(exchange.leftCountCanExchange, affordable).coerceAtLeast(0)
            if (count > 0) {
                add(
                    FishpondTaskAction.Exchange(
                        taskId = exchangeTask.taskId,
                        manurePaid = exchange.manurePriceOfRod * count,
                        rodExchangeCount = count,
                    ),
                )
            }
        }

        // 2026-09-05 实测：VISIT/SUBSCRIBE 类任务可直接 antiep finishTask 完成
        // （如三国冰河30s +1、逛好物 +3、看精选商品 +2、开启提醒 +3）；
        // GOFISH 随钓鱼自然完成，OFFLINE_SHARE 需真实分享，EXCH_MANURE_4_ROD 由
        // 上方兑换动作专属处理，均不在此列。
        snapshot.tasks
            .filter {
                it.taskStatus == "TODO" && it.sceneCode.isNotBlank() &&
                    it.actionType != "OFFLINE_SHARE" && it.actionType != "GOFISH" &&
                    it.actionType != "EXCH_MANURE_4_ROD"
            }
            .forEach { add(FishpondTaskAction.Finish(it.sceneCode, it.taskId, it.title)) }

        snapshot.tasks
            .filter { it.taskStatus == "FINISHED" && it.sceneCode.isNotBlank() }
            .forEach { add(FishpondTaskAction.Receive(it.sceneCode, it.taskId, it.title)) }
    }
}
