package io.github.magisk317.sesame.model

enum class ModelType(
    val code: Int,
    val displayName: String,
) {
    NORMAL(0, "普通模块"),
    TASK(1, "任务模块"),
    ;

    companion object {
        private val byCode = entries.associateBy(ModelType::code)

        @JvmStatic
        fun getByCode(code: Int): ModelType? = byCode[code]
    }

    fun getName(): String = displayName
}
