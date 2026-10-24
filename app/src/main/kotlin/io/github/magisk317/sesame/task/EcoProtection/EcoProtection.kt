package io.github.magisk317.sesame.task.EcoProtection

import io.github.magisk317.sesame.data.Status.Companion.ancientTreeToday
import io.github.magisk317.sesame.data.Status.Companion.canAncientTreeToday
import io.github.magisk317.sesame.entity.AreaCode
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.TaskCommon
import io.github.magisk317.sesame.util.GlobalThreadPools.sleepCompat
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

class EcoProtection : ModelTask() {
    override fun getName(): String? {
        return "生态保护"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.FOREST
    }

    override fun getIcon(): String {
        return "EcoProtection.png"
    }

    private var ancientTreeOnlyWeek: BooleanModelField? = null
    private var ancientTreeCityCodeList: SelectModelField? = null
    public override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(BooleanModelField("ancientTreeOnlyWeek", "仅星期一、三、五运行保护古树", false).also { ancientTreeOnlyWeek = it })
        modelFields.addField(
            SelectModelField(
                "ancientTreeCityCodeList",
                "古树区划代码列表",
                LinkedHashSet<String?>()
            ) { AreaCode.getList() }.also { ancientTreeCityCodeList = it })
        return modelFields
    }

    override fun check(): Boolean {
        if (!super.check()) return false

        if (!TaskCommon.IS_AFTER_8AM) {
            return false
        }

        if (ancientTreeOnlyWeek?.value == true) {
            val sdfWeek = SimpleDateFormat("EEEE", Locale.getDefault())
            val week = sdfWeek.format(Date())
            return "星期一" == week || "星期三" == week || "星期五" == week
        }
        return true
    }

    override suspend fun runSuspend() {
        try {
            ancientTree(ancientTreeCityCodeList!!.value.filterNotNull().toMutableList())
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "古树保护🌳 执行异常:", t)
        }
    }

    companion object {
        private val TAG: String = EcoProtection::class.java.getSimpleName()
        private fun ancientTree(ancientTreeCityCodeList: MutableCollection<String>) {
            try {
                for (cityCode in ancientTreeCityCodeList) {
                    if (!canAncientTreeToday(cityCode)) continue
                    ancientTreeProtect(cityCode)
                    sleepCompat(1000L)
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG, "ancientTree err:",th)
            }
        }

        private fun ancientTreeProtect(cityCode: String) {
            try {
                val jo = EcoProtectionRpcCall.homePage(cityCode).toJsonObject()
                if (ResChecker.checkRes(TAG, jo)) {
                    val data = jo.getJSONObject("data")
                    if (!data.has("districtBriefInfoList")) {
                        return
                    }
                    val districtBriefInfoList = data.getJSONArray("districtBriefInfoList")
                    for (i in 0..<districtBriefInfoList.length()) {
                        val districtBriefInfo = districtBriefInfoList.getJSONObject(i)
                        val userCanProtectTreeNum = districtBriefInfo.optInt("userCanProtectTreeNum", 0)
                        if (userCanProtectTreeNum < 1) continue
                        val districtInfo = districtBriefInfo.getJSONObject("districtInfo")
                        val districtCode = districtInfo.getString("districtCode")
                        districtDetail(districtCode)
                        sleepCompat(1000L)
                    }
                    ancientTreeToday(cityCode)
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG,"ancientTreeProtect err:", th)
            }
        }

        private fun districtDetail(districtCode: String?) {
            try {
                var jo = EcoProtectionRpcCall.districtDetail(districtCode).toJsonObject()
                if (ResChecker.checkRes(TAG, jo)) {
                    var data = jo.getJSONObject("data")
                    if (!data.has("ancientTreeList")) {
                        return
                    }
                    val districtInfo = data.getJSONObject("districtInfo")
                    var cityCode = districtInfo.getString("cityCode")
                    val cityName = districtInfo.getString("cityName")
                    val districtName = districtInfo.getString("districtName")
                    val ancientTreeList = data.getJSONArray("ancientTreeList")
                    for (i in 0..<ancientTreeList.length()) {
                        val ancientTreeItem = ancientTreeList.getJSONObject(i)
                        if (ancientTreeItem.getBoolean("hasProtected")) continue
                        val ancientTreeControlInfo = ancientTreeItem.getJSONObject("ancientTreeControlInfo")
                        val quota = ancientTreeControlInfo.optInt("quota", 0)
                        val useQuota = ancientTreeControlInfo.optInt("useQuota", 0)
                        if (quota <= useQuota) continue
                        val itemId = ancientTreeItem.getString("projectId")
                        val ancientTreeDetail = EcoProtectionRpcCall.projectDetail(itemId, cityCode).toJsonObject()
                        if (ResChecker.checkRes(TAG, ancientTreeDetail)) {
                            data = ancientTreeDetail.getJSONObject("data")
                            if (data.getBoolean("canProtect")) {
                                val currentEnergy = data.getInt("currentEnergy")
                                val ancientTree = data.getJSONObject("ancientTree")
                                val activityId = ancientTree.getString("activityId")
                                val projectId = ancientTree.getString("projectId")
                                val ancientTreeInfo = ancientTree.getJSONObject("ancientTreeInfo")
                                val name = ancientTreeInfo.getString("name")
                                val age = ancientTreeInfo.getInt("age")
                                val protectExpense = ancientTreeInfo.getInt("protectExpense")
                                cityCode = ancientTreeInfo.getString("cityCode")
                                if (currentEnergy < protectExpense) break
                                sleepCompat(200)
                                jo = EcoProtectionRpcCall.protect(activityId, projectId, cityCode).toJsonObject()
                                if (ResChecker.checkRes(TAG, jo)) {
                                    StructuredLog.forest(
                                        ("保护古树🎐[" + cityName + "-" + districtName
                                                + "]#" + age + "年" + name + ",消耗能量" + protectExpense + "g")
                                    )
                                } else {
                                    StructuredLog.error(TAG, "古树保护🌳 ❌ 保护失败")
                                }
                            }
                        } else {
                            StructuredLog.error(TAG, "古树保护🌳 ❌ 详情查询失败")
                        }
                        sleepCompat(500L)
                    }
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG, "districtDetail err:",th)
            }
        }
        init {
            ModelOrder.register(ModelDefinition(EcoProtection::class.java) { EcoProtection() })
        }
    }
}
