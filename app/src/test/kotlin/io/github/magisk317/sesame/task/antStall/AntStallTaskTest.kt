package io.github.magisk317.sesame.task.antStall

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AntStallTaskTest {
    @Test
    fun hiddenInviteShopConfigurationFailsClosed() {
        val task = AntStall()

        assertFalse(task.isStallInviteShopEnabled())
        task.getFields()
        assertFalse(task.isStallInviteShopEnabled())
    }

    @Test
    fun onlyLiveVerifiedTasksUseGenericFinishProtocol() {
        assertTrue(AntStall.usesVerifiedGenericFinish("ANTSTALL_TASK_TBqiandao"))
        assertTrue(AntStall.usesVerifiedGenericFinish("ANTSTALL_TASK_huiyuanjifen1"))
        assertFalse(AntStall.usesVerifiedGenericFinish("ANTSTALL_TASK_taojinbihuanduan"))
        assertFalse(AntStall.usesVerifiedGenericFinish("ANTSTALL_TASK_XCXYX_qingyunjue"))
    }
}
