package io.github.magisk317.sesame.model

enum class ModelGroup(
    val code: String,
    val displayName: String,
    val icon: String,
) {
    BASE("BASE", "基础", "svg/group/base.svg"),
    FOREST("FOREST", "森林", "svg/group/forest.svg"),
    FARM("FARM", "庄园", "svg/group/farm.svg"),
    STALL("STALL", "新村", "svg/group/stall.svg"),
    ORCHARD("ORCHARD", "农场", "svg/group/orchard.svg"),
    SPORTS("SPORTS", "运动", "svg/group/sports.svg"),
    MEMBER("MEMBER", "会员", "svg/group/member.svg"),
    OTHER("OTHER", "其他", "svg/group/other.svg"),
    ;

    companion object {
        private val byCode = entries.associateBy(ModelGroup::code)

        @JvmStatic
        fun getByCode(code: String?): ModelGroup? = byCode[code]

        @JvmStatic
        fun getName(code: String?): String? = getByCode(code)?.displayName
    }

    fun getName(): String = displayName
}
