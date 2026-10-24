package io.github.magisk317.sesame.task.antTravel

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Read-only RPCs captured from the Alipay travel-rights center. */
internal object TravelRightsRpc {
    private const val ACTIVITY_ID = "traffic_coupon_202511"
    private const val CITY_CODE = "110100"
    private const val CHANNEL = "ch_appid-20002047__chsub_pageid-com.alipay.android.phone.xriver.bundlex.CSGAPushActivity"

    fun queryNormalTasks(): String = request(
        "alipay.trafficgrowth.normalTask.query",
        normalTaskPayload(),
    )

    fun queryBenefits(): String = request(
        "alipay.imasp.trip.benefit.dataquery",
        benefitPayload(),
    )

    fun queryMileageExperiment(): String = request(
        "alipay.trafficgrowth.mileage.experiment.query",
        mileageExperimentPayload(),
    )

    fun queryRecommendedBenefits(): String = request(
        "alipay.trafficgrowth.promotion.recommend.benefit.query",
        recommendedBenefitsPayload(),
    )

    internal fun mileageExperimentPayload(): JsonObject = jsonObject(
        "chInfo" to CHANNEL,
        "extParams" to jsonObject(),
    )

    internal fun recommendedBenefitsPayload(): JsonObject = jsonObject(
        "activityId" to ACTIVITY_ID,
        "chInfo" to CHANNEL,
        "cityCode" to CITY_CODE,
    )

    internal fun normalTaskPayload(): JsonObject = jsonObject(
        "activityId" to ACTIVITY_ID,
        "chInfo" to CHANNEL,
        "cityCode" to CITY_CODE,
        "componentCode" to "normalTaskCode",
        "sceneCode" to "chuxingjiaocha",
    )

    internal fun benefitPayload(): JsonObject = jsonObject(
        "bizType" to "",
        "channel" to CHANNEL,
        "cityCode" to CITY_CODE,
        "districtCode" to "",
        "extParams" to jsonObject("showLightsTaskFlag" to "Y"),
        "latitude" to "",
        "longitude" to "",
        "queryType" to jsonArray(
            JsonPrimitive("trafficCouponTabs"),
            JsonPrimitive("trafficCouponProduct"),
            JsonPrimitive("deliverContent"),
            JsonPrimitive("trafficMark"),
            JsonPrimitive("pointInfo"),
        ),
        "scene" to "recommendBenefit",
        "source" to "travel-rights-center",
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
