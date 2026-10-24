package io.github.magisk317.sesame.task.antTravel

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TravelRightsRpcTest {
    @Test
    fun normalTaskPayloadMatchesLiveXriverContract() {
        val payload = TravelRightsRpc.normalTaskPayload()
        assertEquals("traffic_coupon_202511", payload["activityId"]?.jsonPrimitive?.content)
        assertEquals("110100", payload["cityCode"]?.jsonPrimitive?.content)
        assertEquals("normalTaskCode", payload["componentCode"]?.jsonPrimitive?.content)
        assertEquals("chuxingjiaocha", payload["sceneCode"]?.jsonPrimitive?.content)
        assertTrue(payload["chInfo"]?.jsonPrimitive?.content?.contains("CSGAPushActivity") == true)
    }

    @Test
    fun auxiliaryPayloadsMatchObservedContracts() {
        val mileage = TravelRightsRpc.mileageExperimentPayload()
        assertTrue(mileage["chInfo"]?.jsonPrimitive?.content?.contains("CSGAPushActivity") == true)
        assertTrue(mileage["extParams"]?.jsonObject?.isEmpty() == true)

        val recommended = TravelRightsRpc.recommendedBenefitsPayload()
        assertEquals("traffic_coupon_202511", recommended["activityId"]?.jsonPrimitive?.content)
        assertEquals("110100", recommended["cityCode"]?.jsonPrimitive?.content)
        assertTrue(recommended["chInfo"]?.jsonPrimitive?.content?.contains("CSGAPushActivity") == true)
    }

    @Test
    fun benefitPayloadRequestsOnlyObservedReadFields() {
        val payload = TravelRightsRpc.benefitPayload()
        assertEquals("recommendBenefit", payload["scene"]?.jsonPrimitive?.content)
        assertEquals("travel-rights-center", payload["source"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("trafficCouponTabs", "trafficCouponProduct", "deliverContent", "trafficMark", "pointInfo"),
            payload["queryType"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertEquals("Y", payload["extParams"]?.jsonObject?.get("showLightsTaskFlag")?.jsonPrimitive?.content)
    }
}
