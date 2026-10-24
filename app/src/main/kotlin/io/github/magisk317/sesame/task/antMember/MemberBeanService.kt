package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.toJsonObject

/** Handles the independent Blue Bean sign-in and exchange workflows. */
class MemberBeanService {
    fun signIn() {
        try {
            val response = BlueBeanRpc.querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN")
            var result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(result.toString())
                return
            }
            if (!result.getJSONObject("result").getBoolean("canPush")) return

            result = BlueBeanRpc.signIn("AP16242232", "INS_BLUE_BEAN_SIGN").toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                val prize = result.getJSONObject("result")
                    .getJSONArray("prizeSendOrderDTOList")
                    .getJSONObject(0)
                    .getString("prizeName")
                StructuredLog.record(TAG, "安心豆🫘[$prize]")
            } else {
                StructuredLog.record(result.toString())
            }
        } catch (error: NullPointerException) {
            StructuredLog.printStackTrace(TAG, "安心豆🫘[RPC桥接失败]#可能是RpcBridge未初始化", error)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "beanSignIn err:", error)
        }
    }

    fun exchangeBubbleBoost() {
        try {
            var result = BlueBeanRpc.queryAccount("INS_BLUE_BEAN").toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(result.toString())
                return
            }
            val currentPoint = result.getJSONObject("result").getInt("userCurrentPoint")

            result = BlueBeanRpc.exchangeDetail("IT20230214000700069722").toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(result.toString())
                return
            }

            val detail = result.getJSONObject("result")
                .getJSONObject("rspContext")
                .getJSONObject("params")
                .getJSONObject("exchangeDetail")
            val itemId = detail.getString("itemId")
            val itemName = detail.getString("itemName")
            val consult = detail.getJSONObject("itemExchangeConsultDTO")
            val cost = consult.getInt("realConsumePointAmount")
            if (!consult.getBoolean("canExchange") || cost > currentPoint) return

            result = BlueBeanRpc.exchange(itemId, cost).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(TAG, "安心豆🫘[兑换:$itemName]")
            } else {
                StructuredLog.record(result.toString())
            }
        } catch (error: NullPointerException) {
            StructuredLog.printStackTrace(TAG, "安心豆🫘[RPC桥接失败]#可能是RpcBridge未初始化", error)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "beanExchangeBubbleBoost err:", error)
        }
    }

    private companion object {
        const val TAG = "MemberBeanService"
    }
}
