package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestVitalityTaskTest {
    @Test
    fun onlyTodoAndFinishedStatusesProduceBackendActions() {
        assertEquals(
            ForestVitalityTask.Action.FINISH,
            ForestVitalityTask.actionFor("GYG_diantao", "TODO"),
        )
        assertEquals(
            ForestVitalityTask.Action.RECEIVE,
            ForestVitalityTask.actionFor("unknown_task", "FINISHED"),
        )
        assertEquals(
            ForestVitalityTask.Action.SKIP,
            ForestVitalityTask.actionFor("unknown_task", "RECEIVED"),
        )
        assertEquals(
            ForestVitalityTask.Action.SKIP,
            ForestVitalityTask.actionFor("unknown_task", "NOT_TRIGGER"),
        )
        assertEquals(
            ForestVitalityTask.Action.SKIP,
            ForestVitalityTask.actionFor("GYG_diantao", "TODO", true),
        )
        assertEquals(
            ForestVitalityTask.Action.SKIP,
            ForestVitalityTask.actionFor("GYG_TAOBAOzhibo_202606", "TODO"),
        )
    }

    @Test
    fun liveVerifiedTaskCatalogIncludesTheSuccessfulScanBatch() {
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("GYG_diantao"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("DL_ZHONGSHUGONGYILIN_202607"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("GYG-TAOCAICAI"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("LMCT_LLRW_LINSHI"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("GYG_jingxishiji_202602"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("QYJZFM_LLRW_LINSHI"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("DL_SCzhibo_202604"))
        assertTrue(ForestVitalityTask.isVerifiedFinishTask("GYG_BAOXIANyiliao_202606"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("ENERGYRAIN"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("ZHRW_XIAOHEBAOsenlinchajian_202606"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("GYG_taobaoqiandao_202603"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("ZHRW_HUIYUANjiaoshui_202608"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("ZHRW_haoyibaoseyl_202512"))
        assertFalse(ForestVitalityTask.isVerifiedFinishTask("SHARETASK_NEW"))
    }

    @Test
    fun parsesTaskTitleAndProgressFromListTaskopengreen() {
        val task = jsonObject(
            "taskBaseInfo" to jsonObject(
                "taskType" to "GYG_baidu_202506",
                "taskStatus" to "TODO",
                "taskProgress" to 1,
                "taskRequire" to 2,
                "bizInfo" to jsonObject("taskTitle" to "去百度看蚂蚁森林").toString(),
            ),
        )

        assertEquals(
            ForestVitalityTask.TaskInfo(
                "GYG_baidu_202506",
                "TODO",
                "去百度看蚂蚁森林",
                1,
                2,
            ),
            ForestVitalityTask.parseTask(task),
        )
    }

    @Test
    fun malformedTaskEntriesAreIgnoredWithoutLosingValidEntries() {
        val response = jsonObject(
            "taskInfoList" to jsonArray(
                jsonObject("taskBaseInfo" to jsonObject("taskType" to "")),
                jsonObject(
                    "taskBaseInfo" to jsonObject(
                        "taskType" to "TASK_OK",
                        "taskStatus" to "RECEIVED",
                        "bizInfo" to "{broken",
                    ),
                ),
            ),
        )

        val tasks = ForestVitalityTask.parseTasks(response)

        assertEquals(1, tasks.size)
        assertEquals("TASK_OK", tasks.single().title)
    }

    @Test
    fun parsesNestedChildTasksAndKeepsTheirParent() {
        val response = jsonObject(
            "taskInfoList" to jsonArray(
                jsonObject(
                    "taskBaseInfo" to jsonObject(
                        "taskType" to "TEST_LEAF_TASK",
                        "taskStatus" to "TODO",
                        "bizInfo" to jsonObject("taskTitle" to "逛农场得落叶肥料").toString(),
                    ),
                    "childTaskTypeList" to jsonArray(
                        jsonObject(
                            "taskBaseInfo" to jsonObject(
                                "taskType" to "TEST_LEAF_COOLECT_TASK",
                                "taskStatus" to "FINISHED",
                                "bizInfo" to jsonObject("taskTag" to "leafCollectTask").toString(),
                            ),
                        ),
                        jsonObject(
                            "taskBaseInfo" to jsonObject(
                                "taskType" to "TEST_LEAF_CONVERT_TASK",
                                "taskStatus" to "TODO",
                                "bizInfo" to jsonObject("taskTag" to "leafConvertTask").toString(),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val tasks = ForestVitalityTask.parseTasks(response)

        assertEquals(
            listOf("TEST_LEAF_TASK", "TEST_LEAF_COOLECT_TASK", "TEST_LEAF_CONVERT_TASK"),
            tasks.map { it.taskType },
        )
        assertEquals("TEST_LEAF_TASK", tasks[1].parentTaskType)
        assertEquals("leafConvertTask", tasks[2].taskTag)
        assertEquals("leafConvertTask", tasks[2].title)
    }

    @Test
    fun identifiesUnsupportedRpcResponse() {
        assertTrue(ForestVitalityTask.isUnsupported(jsonObject("code" to "400000040")))
        assertFalse(ForestVitalityTask.isUnsupported(jsonObject("code" to "100000000")))
        assertTrue(ForestVitalityTask.isSuccessful(jsonObject("code" to "100000000")))
        assertNull(ForestVitalityTask.parseTask(null))
    }
}
