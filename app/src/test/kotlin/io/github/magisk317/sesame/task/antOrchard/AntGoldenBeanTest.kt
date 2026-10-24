package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AntGoldenBeanTest {
    @Test
    fun `builds sample compatible index and award requests`() {
        val index = AntGoldenBeanRpc.indexArgs().toJsonArray().getJSONObject(0)
        assertEquals("MASTER", index.getString("bizType"))
        assertEquals("babafarm", index.getString("source"))
        assertEquals("20260723.01", index.getString("version"))
        assertTrue(index.getJSONArray("darwinSceneList").isEmpty())

        val award = AntGoldenBeanRpc.receiveTaskAwardArgs("TASK_1").toJsonArray().getJSONObject(0)
        assertEquals("GOLDEN_BEAN_MASTER_TASK", award.getString("sceneCode"))
        assertEquals("TASK_1", award.getString("taskType"))
        assertTrue(award.getBoolean("ignoreLimit"))
        assertEquals("MASTER", award.getJSONObject("bizInfo").getString("bizType"))
    }

    @Test
    fun `applies sample sign and manure guards`() {
        val task = AntGoldenBean()
        assertTrue(task.shouldSign(jsonObject("todaySigned" to false, "currentSignKey" to "key")))
        assertFalse(task.shouldSign(jsonObject("todaySigned" to true, "currentSignKey" to "key")))
        assertFalse(task.shouldSign(jsonObject("todaySigned" to false)))

        // Live contract: todaySigned may be absent; fall back to signList.today.signed.
        assertTrue(
            task.shouldSign(
                jsonObject(
                    "currentSignKey" to "2026-08-19",
                    "signList" to jsonArray(
                        jsonObject(
                            "signKey" to "2026-08-19",
                            "signed" to false,
                            "today" to true,
                        ),
                    ),
                ),
            ),
        )
        assertFalse(
            task.shouldSign(
                jsonObject(
                    "currentSignKey" to "2026-08-19",
                    "signList" to jsonArray(
                        jsonObject(
                            "signKey" to "2026-08-19",
                            "signed" to true,
                            "today" to true,
                        ),
                    ),
                ),
            ),
        )

        val info = jsonObject("currentManure" to 800, "minExchangeAmount" to 200, "remainQuota" to 300)
        assertEquals(250, task.exchangeAmount(info, 250))
        assertEquals(200, task.exchangeAmount(info, 100))
        assertEquals(300, task.exchangeAmount(info, 500))
        assertEquals(0, task.exchangeAmount(jsonObject("currentManure" to 800, "minExchangeAmount" to 200, "remainQuota" to 0), 250))
    }

    @Test
    fun `rejects empty and failed rpc responses`() {
        val task = AntGoldenBean()

        assertThrows(IllegalStateException::class.java) {
            task.requireSuccess("index", "")
        }
        assertThrows(IllegalStateException::class.java) {
            task.requireSuccess(
                "index",
                jsonObject("success" to false, "resultDesc" to "network unavailable").toString(),
            )
        }
    }

    @Test
    fun `parses live index snapshot and plans only auto-finishable tasks`() {
        val snapshot = GoldenBeanFlow.parse(
            jsonObject(
                "success" to true,
                "resultCode" to "100",
                "signInfo" to jsonObject(
                    "currentSignKey" to "2026-08-19",
                    "todaySigned" to false,
                ),
                "manureExchangeInfo" to jsonObject(
                    "currentManure" to 10000,
                    "minExchangeAmount" to 800,
                    "remainQuota" to 5000,
                ),
                "fortuneDrawInfo" to jsonObject("todayDrawn" to false),
                "taskList" to jsonArray(
                    jsonObject(
                        "taskId" to "JINDOULEYUAN_TRIGGER",
                        "taskStatus" to "TODO",
                        "actionType" to "GAMECENTER_TRIGGER",
                        "sceneCode" to "GOLDEN_BEAN_MASTER_TASK",
                        "taskDisplayConfig" to jsonObject("title" to "逛一逛金豆乐园"),
                        "awardCount" to 200,
                    ),
                    jsonObject(
                        "taskId" to "FORTUNE_DRAW_KUAISHOU_PLUS",
                        "taskStatus" to "TODO",
                        "actionType" to "VISIT",
                        "sceneCode" to "GOLDEN_BEAN_MASTER_TASK",
                        "taskDisplayConfig" to jsonObject("title" to "逛一逛快手"),
                        "awardCount" to 1200,
                    ),
                    jsonObject(
                        "taskId" to "GOLDEN_BEAN_TASK_WAKUANG",
                        "taskStatus" to "FINISHED",
                        "actionType" to "TRIGGER",
                        "sceneCode" to "GOLDEN_BEAN_MASTER_TASK",
                        "taskDisplayConfig" to jsonObject("title" to "金猫矿工"),
                        "awardCount" to 100,
                    ),
                    jsonObject(
                        "taskId" to "GOLDEN_BEAN_TASK_ADD_HOME",
                        "taskStatus" to "TODO",
                        "actionType" to "ADD_HOME",
                        "sceneCode" to "GOLDEN_BEAN_MASTER_TASK",
                        "taskDisplayConfig" to jsonObject("title" to "添加首页"),
                    ),
                ),
            ),
        )

        assertEquals("2026-08-19", snapshot.sign?.signKey)
        assertFalse(snapshot.sign!!.todaySigned)
        assertEquals(true, snapshot.fortuneDrawAvailable)
        assertEquals(4, snapshot.tasks.size)

        val actions = GoldenBeanTaskPlanner.actions(
            snapshot = snapshot,
            requestedExchangeAmount = 800,
            signEnabled = true,
            fortuneEnabled = true,
            manureEnabled = true,
            taskAwardEnabled = true,
            attempted = emptySet(),
        )

        assertTrue(actions.any { it is GoldenBeanAction.Sign })
        assertTrue(actions.any { it is GoldenBeanAction.FortuneDraw })
        assertTrue(actions.any { it is GoldenBeanAction.ManureExchange && it.amount == 800 })
        assertTrue(
            actions.any {
                it is GoldenBeanAction.Finish && it.taskType == "JINDOULEYUAN_TRIGGER"
            },
        )
        assertTrue(
            actions.any {
                it is GoldenBeanAction.Receive && it.taskType == "GOLDEN_BEAN_TASK_WAKUANG"
            },
        )
        assertFalse(
            actions.any {
                it is GoldenBeanAction.Finish && it.taskType == "FORTUNE_DRAW_KUAISHOU_PLUS"
            },
        )
        assertFalse(
            actions.any {
                it is GoldenBeanAction.Finish && it.taskType == "GOLDEN_BEAN_TASK_ADD_HOME"
            },
        )
    }

    @Test
    fun `finish task payload keeps live antorchard contract`() {
        val finish = AntGoldenBeanRpc.finishTaskArgs("JINDOULEYUAN_TRIGGER", "2088").toJsonArray().getJSONObject(0)
        assertEquals("MASTER", finish.getString("bizType"))
        assertEquals("GOLDEN_BEAN_MASTER_TASK", finish.getString("sceneCode"))
        assertEquals("JINDOULEYUAN_TRIGGER", finish.getString("taskType"))
        assertEquals("babafarm", finish.getString("source"))
        assertEquals("MASTER", finish.getJSONObject("finishBusinessInfo").getString("bizType"))
        assertTrue(finish.getString("outBizNo").startsWith("2088"))
    }
}
