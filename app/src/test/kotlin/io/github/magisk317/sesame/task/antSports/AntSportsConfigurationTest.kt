package io.github.magisk317.sesame.task.antSports

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AntSportsConfigurationTest {
    private val hiddenFeatureCodes = listOf(
        "sportsTasks",
        "sportsEnergyBubble",
        "receiveCoinAsset",
        "donateCharityCoin",
        "donateCharityCoinType",
        "donateCharityCoinAmount",
        "battleForFriends",
        "battleForFriendType",
        "originBossIdList",
        "trainFriend",
        "zeroCoinLimit",
        "tiyubiz",
    )

    @Test
    fun `keeps legacy sports features out of settings`() {
        val fields = AntSports().getFields()

        hiddenFeatureCodes.forEach { fieldCode ->
            assertFalse(fields.containsKey(fieldCode), "$fieldCode should remain hidden")
        }

        assertTrue(fields.containsKey("walk"))
        assertTrue(fields.containsKey("syncStepCount"))
        assertTrue(fields.containsKey("neverlandTask"))
        assertTrue(fields.containsKey("neverlandGrid"))
        assertTrue(fields.containsKey("neverlandGridStepCount"))
    }

    @Test
    fun `treats fields as disabled before configuration is initialized`() {
        val model = AntSports()

        assertFalse(model.isSportsTasksEnabled())
        assertFalse(model.isSportsEnergyBubbleEnabled())
        assertFalse(model.isReceiveCoinAssetEnabled())
        assertFalse(model.isDonationEnabled())
        assertFalse(model.isNeverlandTaskEnabled())
        assertFalse(model.isNeverlandGridEnabled())
        assertFalse(model.isBattleForFriendsEnabled())
        assertFalse(model.isTrainFriendEnabled())
        assertFalse(model.isTiyubizEnabled())
    }

    @Test
    fun `enables health island features by default while preserving explicit opt out`() {
        val model = AntSports()
        val fields = model.getFields()
        val taskField = fields.getField("neverlandTask")
            as io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
        val gridField = fields.getField("neverlandGrid")
            as io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField

        assertTrue(taskField.defaultValue)
        assertTrue(gridField.defaultValue)
        assertTrue(taskField.value)
        assertTrue(gridField.value)
        assertTrue(model.isNeverlandTaskEnabled())
        assertTrue(model.isNeverlandGridEnabled())

        taskField.value = false
        gridField.value = false
        assertFalse(model.isNeverlandTaskEnabled())
        assertFalse(model.isNeverlandGridEnabled())

        taskField.reset()
        gridField.reset()
        assertTrue(model.isNeverlandTaskEnabled())
        assertTrue(model.isNeverlandGridEnabled())
    }
}
