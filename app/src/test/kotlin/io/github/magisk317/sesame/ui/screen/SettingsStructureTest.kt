package io.github.magisk317.sesame.ui.screen

import io.github.magisk317.sesame.model.ModelConfig
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SettingsStructureTest {
    @Test
    fun `first level keeps all model groups and models follow registry order`() {
        val lateForest = config("LateForest", "后置森林", ModelGroup.FOREST)
        val sports = config("Sports", "运动", ModelGroup.SPORTS)
        val earlyForest = config("EarlyForest", "前置森林", ModelGroup.FOREST)
        val unknownGroup = config("Fallback", "缺失分组", null)

        val groups = buildSettingsGroups(
            configs = listOf(lateForest, sports, unknownGroup, earlyForest),
            modelOrderCodes = listOf("EarlyForest", "LateForest", "Sports", "Fallback"),
        )

        assertEquals(ModelGroup.entries, groups.map(SettingsGroupSection::group))
        assertEquals(
            listOf("EarlyForest", "LateForest"),
            groups.single { it.group == ModelGroup.FOREST }.configs.map(ModelConfig::code),
        )
        assertEquals(
            listOf("Fallback"),
            groups.single { it.group == ModelGroup.OTHER }.configs.map(ModelConfig::code),
        )
    }

    @Test
    fun `repeated ascii and full width prefixes become sections without mutating fields`() {
        val enable = field("enable", "开启新村")
        val stallSwitch = field("stallSwitch", "摆摊 | 开启")
        val stallFriends = field("stallFriends", "摆摊｜好友列表")
        val oneOff = field("oneOff", "邀请 | 仅出现一次")
        val plain = field("plain", "普通选项")
        val model = config(
            code = "Stall",
            name = "新村",
            group = ModelGroup.STALL,
            fields = arrayOf(enable, stallSwitch, stallFriends, oneOff, plain),
        )

        val layout = buildSettingsFieldLayout(model)

        assertSame(enable, layout.enable?.field)
        assertEquals("开启新村", layout.enable?.displayName)
        assertEquals(listOf("摆摊"), layout.sections.map(SettingsFieldSection::title))
        assertEquals(
            listOf("邀请 | 仅出现一次", "普通选项"),
            layout.regularFields.map(SettingsFieldItem::displayName),
        )
        assertEquals(
            listOf("开启", "好友列表"),
            layout.sections[0].fields.map(SettingsFieldItem::displayName),
        )
        assertEquals("摆摊 | 开启", stallSwitch.name)
        assertEquals("stallSwitch", stallSwitch.code)
    }

    @Test
    fun `search target uses stable group model and field codes`() {
        val friendField = field("friendList", "摆摊 | 好友列表", "选择允许摆摊的好友")
        val stall = config(
            code = "AntStall",
            name = "蚂蚁新村",
            group = ModelGroup.STALL,
            fields = arrayOf(friendField),
        )
        val groups = buildSettingsGroups(
            configs = listOf(stall),
            modelOrderCodes = listOf("AntStall"),
        )

        val fieldResult = buildSettingsSearchResults(groups, "好友").single()
        assertEquals(
            SettingsSearchTarget(
                group = ModelGroup.STALL,
                modelCode = "AntStall",
                fieldCode = "friendList",
            ),
            fieldResult.target,
        )

        val modelResult = buildSettingsSearchResults(groups, "AntStall").single()
        assertEquals(ModelGroup.STALL, modelResult.target.group)
        assertEquals("AntStall", modelResult.target.modelCode)
        assertNull(modelResult.target.fieldCode)
    }

    private fun config(
        code: String,
        name: String,
        group: ModelGroup?,
        fields: Array<BooleanModelField> = emptyArray(),
    ): ModelConfig = ModelConfig().apply {
        this.code = code
        this.name = name
        this.group = group
        fields.forEach(this.fields::addField)
    }

    private fun field(
        code: String,
        name: String,
        desc: String? = null,
    ): BooleanModelField = BooleanModelField(code, name, false).also { it.desc = desc }
}
