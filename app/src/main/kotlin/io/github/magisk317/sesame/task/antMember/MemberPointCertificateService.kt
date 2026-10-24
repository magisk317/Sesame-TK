package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay

/** Owns paginated member-point certificate queries and reward collection. */
internal class MemberPointCertificateService {
    suspend fun collect(page: Int, pageSize: Int) {
        try {
            var response = MemberBenefitsRpc.queryPointCert(page, pageSize).toJsonObject()
            delay(500)
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.record(TAG, response.getString("resultDesc"))
                return
            }
            val hasNextPage = response.getBoolean("hasNextPage")
            val certificates = response.getJSONArray("certList")
            for (index in certificates.indices) {
                val certificate = certificates.getJSONObject(index)
                val title = certificate.getString("bizTitle")
                val id = certificate.getString("id")
                val points = certificate.getInt("pointAmount")
                response = MemberBenefitsRpc.receivePointByUser(id).toJsonObject()
                if (ResChecker.checkRes(TAG, response)) {
                    StructuredLog.other("会员积分🎖️[领取$title]#${points}积分")
                } else {
                    StructuredLog.record(TAG, response.getString("resultDesc"))
                    StructuredLog.record(response.toString())
                }
            }
            if (hasNextPage) collect(page + 1, pageSize)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryPointCert err:", error)
        }
    }

    private companion object {
        const val TAG = "MemberPointCertificateService"
    }
}
