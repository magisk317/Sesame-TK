package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OceanTaskPolicyTest {
    @Test
    fun onlyLiveVerifiedExternalTaskUsesGenericFinish() {
        assertFalse(
            OceanTaskPolicy.shouldSkipGenericFinish(
                jsonObject("finishJump" to true),
                "FKSSJ_LLRW_HAIYANG",
            ),
        )
        assertTrue(
            OceanTaskPolicy.shouldSkipGenericFinish(
                jsonObject("finishJump" to true),
                "LYJX_QDRW_HAIYANG",
            ),
        )
        assertTrue(
            OceanTaskPolicy.shouldSkipGenericFinish(
                jsonObject("finishJump" to true),
                "mokuai_senlin_hydrw",
            ),
        )
        assertFalse(
            OceanTaskPolicy.shouldSkipGenericFinish(
                jsonObject("finishJump" to false),
                "some_normal_task",
            ),
        )
    }

    @Test
    fun blockedTaskMatchesTitleOrTaskType() {
        val blocked = setOf("已知标题", "KNOWN_TYPE")

        assertTrue(OceanTaskPolicy.isBlocked(blocked, "已知标题", "other"))
        assertTrue(OceanTaskPolicy.isBlocked(blocked, "other", "KNOWN_TYPE"))
        assertFalse(OceanTaskPolicy.isBlocked(blocked, "other", "other"))
    }
}
