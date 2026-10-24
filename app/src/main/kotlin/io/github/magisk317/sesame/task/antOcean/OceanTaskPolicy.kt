package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.optBoolean
import kotlinx.serialization.json.JsonObject

/** Conservative policy for tasks whose completion requires the external Alipay page/game. */
internal object OceanTaskPolicy {
    /**
     * Live RPC probe on 2026-08-28:
     *
     * com.alipay.antiep.finishTask(
     *   outBizNo=<taskType>_<random>, requestType=RPC,
     *   sceneCode=ANTOCEAN_TASK, source=ANTFOCEAN,
     *   taskType=FKSSJ_LLRW_HAIYANG, uniqueId=<generated>
     * ) returned 100000000; receiveTaskAward returned 100000000 and
     * queryTaskList confirmed TODO -> FINISHED -> RECEIVED.
     *
     * Re-probed on 2026-09-05 through the in-host debug RPC:
     * - LYJX_LLRW_HAIYANG (烈焰觉醒): finishTask + receiveTaskAward both 100000000.
     * - BUSINESS_LIGHTS03 (逛一逛市集): finishTask + receiveTaskAward both 100000000;
     *   repeatable, rightsTimesLimit=3.
     * - KBXY_QDRW_HAIYANG (狂暴西游) and mokuai_senlin_hydrw (玩一玩得拼图) still
     *   return 400000040 (不支持rpc调用) and stay TODO; keep them UI-only.
     */
    private val VERIFIED_GENERIC_FINISH_TASK_TYPES = setOf(
        "FKSSJ_LLRW_HAIYANG",
        "LYJX_LLRW_HAIYANG",
        "BUSINESS_LIGHTS03",
    )

    /**
     * finishJump marks an external page/game flow. Only task types with a complete
     * live finish/receive/status-confirmation chain may use generic finishTask.
     */
    fun shouldSkipGenericFinish(bizInfo: JsonObject, taskType: String): Boolean =
        bizInfo.optBoolean("finishJump") && taskType !in VERIFIED_GENERIC_FINISH_TASK_TYPES

    fun isBlocked(blocked: Set<String>, title: String, taskType: String): Boolean =
        title in blocked || taskType in blocked
}
