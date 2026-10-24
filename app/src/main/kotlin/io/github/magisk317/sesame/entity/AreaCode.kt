package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.toJsonArray

/** Selectable city code loaded from the local city-code file. */
class AreaCode(
    id: String,
    name: String,
) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }

    companion object {
        private const val TAG = "AreaCode"
        private var list: List<AreaCode>? = null

        @JvmStatic
        fun getList(): List<AreaCode> {
            list?.let { return it }
            val result = buildList {
                val cities = parseCityCode(Files.readFromFile(Files.getCityCodeFile()))
                for (index in 0 until cities.length()) {
                    runCatching {
                        val city = cities.getJSONObject(index)
                        add(AreaCode(city.getString("cityCode"), city.getString("cityName")))
                    }.onFailure { StructuredLog.printStackTrace(TAG, it) }
                }
            }
            return result.also { list = it }
        }

        private fun parseCityCode(raw: String) = runCatching {
            raw.toJsonArray()
        }.getOrElse {
            StructuredLog.record(TAG, "parseCityCode failed; using default cities")
            """[
                    {"cityCode":"350100","cityName":"福州市"},
                    {"cityCode":"440100","cityName":"广州市"},
                    {"cityCode":"330100","cityName":"杭州市"},
                    {"cityCode":"370100","cityName":"济南市"},
                    {"cityCode":"320100","cityName":"南京市"},
                    {"cityCode":"430100","cityName":"长沙市"}
                ]""".toJsonArray()
        }
    }
}
