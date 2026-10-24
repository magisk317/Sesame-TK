package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmHireServiceTest {
    @Test
    fun eligibilityRespectsSelectionModeAndCurrentUser() {
        val canHire = jsonArray(JsonPrimitive("can_hire_action"))
        val selected = setOf<String?>("friend")

        assertTrue(FarmHireService.isEligible("friend", canHire, selected, false, "self"))
        assertTrue(FarmHireService.isEligible("other", canHire, selected, true, "self"))
        assertFalse(FarmHireService.isEligible("friend", canHire, selected, true, "self"))
        assertFalse(FarmHireService.isEligible("self", canHire, selected, false, "self"))
        assertFalse(FarmHireService.isEligible("friend", jsonArray(JsonPrimitive("other_action")), selected, false, "self"))
    }
}
