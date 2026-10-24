package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AntAiFishTest {
    @Test
    fun finishAndReceiveUseDifferentProtocolFields() {
        val finish = AntAiFishRpcCall.taskArgs("TASK", includeOutBizNo = true, ignoreLimit = null)
        val receive = AntAiFishRpcCall.taskArgs("TASK", includeOutBizNo = false, ignoreLimit = true)

        assertTrue(finish.containsKey("outBizNo"))
        assertFalse(receive.containsKey("outBizNo"))
        assertEquals("TASK", receive.optString("taskType"))
        assertEquals("ANTAIFISH", receive.optString("sceneCode"))
        assertEquals("true", receive["ignoreLimit"].toString())
        assertFalse(finish.containsKey("ignoreLimit"))
    }

    @Test
    fun rescueFinishUsesObservedH5Protocol() {
        val args = AntAiFishRpcCall.rescueTaskArgs("AIFISH_RESCUE_BROWSE_DGLS_15S")

        assertEquals("H5", args.optString("requestType"))
        assertEquals("ANTAIFISH_RESCUE_AND_RESTORE", args.optString("sceneCode"))
        assertEquals("ANTAIFISH", args.optString("source"))
        assertEquals("AIFISH_RESCUE_BROWSE_DGLS_15S", args.optString("taskType"))
        assertTrue(args.optString("outBizNo").startsWith("AIFISH_RESCUE_BROWSE_DGLS_15S_"))
    }

    @Test
    fun currentPageStateDoesNotTriggerWhenNoTouchChanceRemains() {
        val homepage = jsonObject(
            "myFish" to jsonObject(
                "interactVO" to jsonObject(
                    "fishInteractStatus" to "HAVE_FISH",
                    "remainTouchChance" to 0,
                ),
            )
        )
        val interact = homepage.optJSONObject("myFish")!!.optJSONObject("interactVO")!!
        assertEquals(0, interact.optInt("remainTouchChance", -1))
        assertEquals("HAVE_FISH", interact.optString("fishInteractStatus"))
        assertFalse(AntAiFish.canTouchFish(homepage))
    }

    @Test
    fun onlyExplicitlyAvailableFishCanBeTouched() {
        val homepage = jsonObject(
            "myFish" to jsonObject(
                "interactVO" to jsonObject(
                    "fishInteractStatus" to "HAVE_FISH",
                    "remainTouchChance" to 1,
                ),
            ),
        )
        assertTrue(AntAiFish.canTouchFish(homepage))
    }

    @Test
    fun rescueFishUsesOceanSourceAndClientUniqueId() {
        val args = AntAiFishRpcCall.rescueArgs()

        assertEquals("ANT_OCEAN", args.optString("source"))
        assertTrue(args.optString("uniqueId").isNotBlank())
    }

    @Test
    fun capturedStateSkipsTouchingButIsDistinctFromUnknownState() {
        val captured = jsonObject("fishStatus" to "CAPTURED")
        val unknown = jsonObject("fishStatus" to "FISHING")

        assertEquals("CAPTURED", captured.optString("fishStatus"))
        assertTrue(AntAiFish.isCaptured(captured))
        assertFalse(AntAiFish.isCaptured(unknown))
        assertFalse(AntAiFish.isCaptured(jsonObject()))
    }

    @Test
    fun plannerUsesBackendRpcForTodoAndClaimsFinished() {
        val response = """
            {
              "taskInfoList": [
                {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"TODO_TASK","taskStatus":"TODO","bizInfo":"{\"taskTitle\":\"后台任务\"}"},"taskRights":{"awardCount":1}},
                {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"FINISHED_TASK","taskStatus":"FINISHED","bizInfo":"{\"taskTitle\":\"待领奖\"}"},"taskRights":{"awardCount":2}},
                {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"RECEIVED_TASK","taskStatus":"RECEIVED"}},
                {"taskBaseInfo":{"sceneCode":"OTHER","taskType":"OTHER_TASK","taskStatus":"TODO"}}
              ]
            }
        """.trimIndent().toJsonObject()

        val actions = AntAiFishTaskPlanner.plan(response)
        assertEquals(listOf(AntAiFishTaskActionType.FINISH, AntAiFishTaskActionType.RECEIVE_AWARD), actions.map { it.type })
        assertEquals(listOf("TODO_TASK", "FINISHED_TASK"), actions.map { it.taskType })
    }

    @Test
    fun malformedTaskIsIgnoredWithoutPageFields() {
        val response = """
            {"taskInfoList":[{}, {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskStatus":"TODO"}}, {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"VALID","taskStatus":"TODO"}}]}
        """.trimIndent().toJsonObject()

        assertEquals(listOf("VALID"), AntAiFishTaskPlanner.plan(response).map { it.taskType })
    }

    @Test
    fun repeated_task_entries_are_not_dropped_by_task_type() {
        val response = """
            {
              "taskInfoList": [
                {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"REPEAT_TASK","taskStatus":"TODO"}},
                {"taskBaseInfo":{"sceneCode":"ANTAIFISH","taskType":"REPEAT_TASK","taskStatus":"TODO"}}
              ]
            }
        """.trimIndent().toJsonObject()

        assertEquals(2, AntAiFishTaskPlanner.plan(response).size)
    }

    @Test
    fun rewardDescriptionUsesFirstNamedEntry() {
        val result = jsonObject(
            "touchRewardList" to jsonArray(
                jsonObject(
                    "extInfo" to jsonObject(
                        "popup" to jsonObject(
                            "desc" to "摸到纯能量66g",
                            "name" to "摸到纯能量66g",
                            "rightsNums" to "66",
                        ),
                    ),
                ),
                jsonObject(
                    "extInfo" to jsonObject(
                        "popup" to jsonObject(
                            "desc" to "摸到金币",
                            "name" to "金币",
                            "rightsNums" to "5",
                        ),
                    ),
                ),
            ),
        )
        assertEquals("摸到纯能量66g", AntAiFish.touchRewardDescription(result))
    }

    @Test
    fun rewardDescriptionFallsBackToRightsNumsOrEmpty() {
        val rightsOnly = jsonObject(
            "touchRewardList" to jsonArray(
                jsonObject(
                    "extInfo" to jsonObject(
                        "popup" to jsonObject("rightsNums" to "10"),
                    ),
                ),
            ),
        )
        assertEquals("能量10g", AntAiFish.touchRewardDescription(rightsOnly))

        val noNamedReward = jsonObject(
            "touchRewardList" to jsonArray(
                jsonObject("extInfo" to jsonObject()),
                jsonObject(),
            ),
        )
        assertEquals("", AntAiFish.touchRewardDescription(noNamedReward))
        assertEquals("", AntAiFish.touchRewardDescription(jsonObject()))
    }

    @Test
    fun planner_signature_changes_when_finish_becomes_claim() {
        val finish = AntAiFishTaskAction(
            AntAiFishTaskActionType.FINISH,
            "TASK",
            "title",
            1,
        )
        val receive = finish.copy(type = AntAiFishTaskActionType.RECEIVE_AWARD)

        assertFalse(AntAiFishTaskPlanner.signature(listOf(finish)) ==
            AntAiFishTaskPlanner.signature(listOf(receive)))
        assertTrue(AntAiFishTaskPlanner.signature(listOf(finish)) ==
            AntAiFishTaskPlanner.signature(listOf(finish)))
    }
}
