package io.github.magisk317.sesame.task.antMember

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AntMemberConfigurationTest {
    @Test
    fun `sesame credit task and collection switches are exposed`() {
        val fields = AntMember().getFields()

        assertNotNull(fields.getField("sesameTask"))
        assertEquals("芝麻信用|芝麻粒信用任务", fields.getField("sesameTask")?.name)
        assertNotNull(fields.getField("collectSesame"))
        assertNotNull(fields.getField("collectSesameWithOneClick"))
    }
}
