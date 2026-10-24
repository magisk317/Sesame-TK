package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay

/** Claims available insured-gold rewards. */
class MemberInsuredGoldService {
    suspend fun collect() {
        try {
            var response = InsuredGoldRpc.queryAvailable()
            delay(200)
            var result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.error("$TAG.query", "保障金🏥[响应失败]#$response")
                return
            }
            val data = result.getJSONObject("data")
            val signIn = data.getJSONObject("signInDTO")
            val pending = data.getJSONArray("eventToWaitDTOList")
            if (signIn.getInt("sendFlowStatus") == 1 && signIn.getInt("sendType") == 1) {
                response = InsuredGoldRpc.collect(signIn.toString().toJsonObject())
                delay(2000)
                result = response.toJsonObject()
                if (!ResChecker.checkRes(TAG, result)) {
                    StructuredLog.error("$TAG.collect", "保障金🏥[响应失败]#$response")
                    return
                }
                StructuredLog.other("保障金🏥[领取保证金]#+${result.getJSONObject("data").getString("gainSumInsuredYuan")}元")
            }
            for (index in pending.indices) {
                response = InsuredGoldRpc.collect(pending.getJSONObject(index).toString().toJsonObject())
                delay(2000)
                result = response.toJsonObject()
                if (!ResChecker.checkRes(TAG, result)) {
                    StructuredLog.error("$TAG.collect", "保障金🏥[响应失败]#$response")
                    return
                }
                val gain = result.getJSONObject("data").getJSONObject("gainSumInsuredDTO")
                    .getString("gainSumInsuredYuan")
                StructuredLog.other("保障金🏥[领取保证金]+${gain}元")
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "collect err", error)
        }
    }

    private companion object {
        const val TAG = "MemberInsuredGoldService"
    }
}
