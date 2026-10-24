package io.github.magisk317.sesame.task.antTravel

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TravelRightsStateTest {
    @Test
    fun parsesObservedBenefitAndTaskFieldsWithoutInferringActions() {
        val benefit = TravelRightsStateParser.parseBenefits(benefitFixture())
        val tasks = TravelRightsStateParser.parseTasks(taskFixture())

        assertEquals("40", benefit?.currentPoint)
        assertEquals("10225", benefit?.totalPoint)
        assertTrue(benefit?.signOpen ?: false)
        assertFalse(benefit?.signed ?: true)
        assertEquals(12, benefit?.couponTabCount)
        assertEquals(29, benefit?.couponProductCount)
        assertEquals(1, tasks.size)
        assertEquals("TT2026063002393128", tasks.single().taskCode)
        assertEquals("逛游戏中心 玩游戏赚现金", tasks.single().title)
        assertEquals("claim", tasks.single().status)
        assertEquals("exposureAutoTake", tasks.single().claimType)
        assertEquals(2_000L, tasks.single().browseTimeMs)
        assertEquals(TravelRightsTaskAction.SKIP, tasks.single().action)
    }

    @Test
    fun distinguishesFailedTaskQueryFromAnEmptySuccessfulTaskList() {
        assertTrue(TravelRightsStateParser.isSuccessful("{\"success\":true,\"code\":\"SUCCESS\"}".toJsonObject()))
        assertFalse(TravelRightsStateParser.isSuccessful("{\"success\":false}".toJsonObject()))
        assertNull(TravelRightsStateParser.parseTasksOrNull("{\"success\":false}".toJsonObject()))
        assertTrue(
            TravelRightsStateParser.parseTasksOrNull(
                "{\"success\":true,\"code\":\"SUCCESS\",\"taskList\":[]}".toJsonObject(),
            )?.isEmpty() == true,
        )
    }

    @Test
    fun rejectsUnsuccessfulBenefitsAndMalformedTasks() {
        assertNull(TravelRightsStateParser.parseBenefits("{\"success\":false}".toJsonObject()))
        assertTrue(TravelRightsStateParser.parseTasks("{\"success\":true,\"code\":\"SUCCESS\",\"taskList\":[{}]}".toJsonObject()).isEmpty())
    }

    @Test
    fun marksFinishedTasksForRewardAndLeavesReturnedTasksAlone() {
        val response = """
            {"success":true,"code":"SUCCESS","taskList":[
              {"taskCode":"FINISHED","taskStatus":"FINISHED","recordNo":"R1","displayInfo":{"taskName":"done"}},
              {"taskCode":"RECEIVED","taskStatus":"RECEIVED","displayInfo":{"taskName":"received"}},
              {"taskCode":"TODO_NO_PAGE","taskStatus":"TODO","displayInfo":{"taskName":"no page"}}
            ]}
        """.trimIndent().toJsonObject()

        val tasks = TravelRightsStateParser.parseTasks(response)
        assertEquals(TravelRightsTaskAction.RECEIVE, tasks[0].action)
        assertEquals("R1", tasks[0].recordNo)
        assertEquals(TravelRightsTaskAction.SKIP, tasks[1].action)
        assertEquals(TravelRightsTaskAction.SKIP, tasks[2].action)
    }

    private fun benefitFixture() = """
        {
          "success": true,
          "code": "SUCCESS",
          "pointInfo": {"currentPoint":"40","totalPoint":"10225"},
          "signInfo": {"openSignFlag":true,"signInFlag":false},
          "trafficCouponTabs":[{},{} ,{},{} ,{},{} ,{},{} ,{},{} ,{},{}],
          "trafficCouponProduct":[{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{}]
        }
    """.trimIndent().toJsonObject()

    private fun taskFixture() = """
        {
          "success": true,
          "code": "SUCCESS",
          "taskList": [{
            "taskCode":"TT2026063002393128",
            "taskStatus":"claim",
            "claimType":"exposureAutoTake",
            "advanceType":"userPush",
            "displayInfo":{"taskName":" 逛游戏中心 玩游戏赚现金","taskProgressPercentage":1,"browseTime":2}
          }]
        }
    """.trimIndent().toJsonObject()
}
