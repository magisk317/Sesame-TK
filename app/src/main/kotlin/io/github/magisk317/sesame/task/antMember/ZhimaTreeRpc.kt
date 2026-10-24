package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC gateway for the Zhima Tree activity. */
object ZhimaTreeRpc {
    private const val PLAY_INFO = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3"
    private const val REFER = "https://render.alipay.com/p/yuyan/180020010001269849/zmTree.html?caprMode=sync&chInfo=chInfo=ch_zmzltf__chsub_xinyongsyyingxiaowei"

    fun home(): String? = trigger("ZHIMA_TREE_HOME_PAGE", jsonObject())
    fun cleanAndPush(treeCode: String): String? = trigger("ZHIMA_TREE_CLEAN_AND_PUSH", jsonObject("clickNum" to "1", "treeCode" to treeCode))
    fun queryRentGreenTaskList(): String? = trigger(
        "RENT_GREEN_TASK_LIST_QUERY",
        jsonObject("chInfo" to "ch_share__chsub_ALPContact", "batchId" to ""),
    )
    fun finishRentGreenTask(taskId: String, stageCode: String): String? = trigger(
        "RENT_GREEN_TASK_FINISH",
        jsonObject("chInfo" to "ch_share__chsub_ALPContact", "taskId" to taskId, "stageCode" to stageCode),
    )

    private fun trigger(operation: String, extInfo: JsonObject): String? = runCatching {
        val args = jsonObject("operation" to operation, "playInfo" to PLAY_INFO, "refer" to REFER, "extInfo" to extInfo)
        RequestManager.requestString("alipay.promoprod.play.trigger", jsonArray(args).toString())
    }.getOrNull()
}
