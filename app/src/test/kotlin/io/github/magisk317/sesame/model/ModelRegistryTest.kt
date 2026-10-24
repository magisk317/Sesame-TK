package io.github.magisk317.sesame.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelRegistryTest {
    @Test
    fun lifecycleRebuildsAndClearsEveryRegistryIndex() {
        Model.initAllModel()
        try {
            assertFalse(Model.getModelConfigMap().isEmpty())
            assertTrue(Model.getModelArray().isNotEmpty())
            assertFalse(Model.getGroupModelConfig(ModelGroup.BASE).isEmpty())
        } finally {
            Model.destroyAllModel()
        }

        assertTrue(Model.getModelConfigMap().isEmpty())
        assertTrue(Model.getGroupModelConfig(ModelGroup.BASE).isEmpty())
        assertTrue(Model.getModelArray().isEmpty())
    }
}
