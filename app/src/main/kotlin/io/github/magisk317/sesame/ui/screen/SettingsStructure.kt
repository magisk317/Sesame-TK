package io.github.magisk317.sesame.ui.screen

import io.github.magisk317.sesame.model.ModelConfig
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.ModelOrder
import java.util.Locale

internal data class SettingsGroupSection(
    val group: ModelGroup,
    val configs: List<ModelConfig>,
)

internal data class SettingsFieldItem(
    val field: ModelField<*>,
    val displayName: String,
)

internal data class SettingsFieldSection(
    val title: String,
    val fields: List<SettingsFieldItem>,
)

internal data class SettingsModelFields(
    val enable: SettingsFieldItem?,
    val regularFields: List<SettingsFieldItem>,
    val sections: List<SettingsFieldSection>,
)

internal data class SettingsSearchTarget(
    val group: ModelGroup,
    val modelCode: String,
    val fieldCode: String?,
)

internal data class SettingsSearchResult(
    val target: SettingsSearchTarget,
    val groupName: String,
    val modelName: String,
    val fieldName: String?,
)

/**
 * Builds the fixed first-level settings navigation. The enum is the navigation
 * contract, so empty groups are retained and unexpected models are never used
 * to create ad-hoc top-level destinations.
 */
internal fun buildSettingsGroups(
    configs: List<ModelConfig>,
    modelOrderCodes: List<String> = ModelOrder.all.map { it.type.simpleName },
): List<SettingsGroupSection> {
    val orderByCode = modelOrderCodes.withIndex().associate { (index, code) -> code to index }
    val sourceIndex = configs.withIndex().associate { (index, config) -> config to index }

    return ModelGroup.entries.map { group ->
        val groupConfigs = configs
            .filter { (it.group ?: ModelGroup.OTHER) == group }
            .sortedWith(
                compareBy<ModelConfig> { orderByCode[it.code] ?: Int.MAX_VALUE }
                    .thenBy { sourceIndex.getValue(it) },
            )
        SettingsGroupSection(group = group, configs = groupConfigs)
    }
}

/**
 * Pins the model switch above the remaining fields and derives visual
 * subsections from repeated name prefixes. Fields without a repeated prefix
 * stay inline so the renderer does not invent a distracting "常规" section.
 * Field objects, names, codes and values are intentionally left untouched.
 */
internal fun buildSettingsFieldLayout(config: ModelConfig): SettingsModelFields {
    val fields = config.fields.values.toList()
    val enable = fields.firstOrNull { it.code == "enable" }
        ?.let { SettingsFieldItem(field = it, displayName = it.name) }
    val configurableFields = fields.filterNot { it.code == "enable" }
    val parsedNames = configurableFields.associateWith { parseSectionName(it.name) }
    val prefixCounts = parsedNames.values
        .filterNotNull()
        .groupingBy(ParsedSectionName::prefix)
        .eachCount()

    val regular = mutableListOf<SettingsFieldItem>()
    val namedSections = linkedMapOf<String, MutableList<SettingsFieldItem>>()
    configurableFields.forEach { field ->
        val parsed = parsedNames[field]
        if (parsed != null && prefixCounts.getValue(parsed.prefix) >= 2) {
            namedSections.getOrPut(parsed.prefix) { mutableListOf() }
                .add(SettingsFieldItem(field = field, displayName = parsed.remainder))
        } else {
            regular += SettingsFieldItem(field = field, displayName = field.name)
        }
    }

    val sections = namedSections.map { (title, sectionFields) ->
        SettingsFieldSection(title = title, fields = sectionFields)
    }
    return SettingsModelFields(
        enable = enable,
        regularFields = regular,
        sections = sections,
    )
}

internal fun buildSettingsSearchResults(
    groups: List<SettingsGroupSection>,
    rawQuery: String,
): List<SettingsSearchResult> {
    val query = rawQuery.trim().lowercase(Locale.ROOT)
    if (query.isEmpty()) return emptyList()

    return groups.flatMap { section ->
        section.configs.flatMap { config ->
            val modelCode = config.code.orEmpty()
            val modelName = config.name.orEmpty()
            val baseTarget = SettingsSearchTarget(
                group = section.group,
                modelCode = modelCode,
                fieldCode = null,
            )
            val modelMatches = listOf(
                section.group.displayName,
                section.group.code,
                modelName,
                modelCode,
            ).any { it.contains(query, ignoreCase = true) }
            val fieldResults = config.fields.values.mapNotNull { field ->
                val searchable = listOfNotNull(field.name, field.code, field.desc)
                if (searchable.any { it.lowercase(Locale.ROOT).contains(query) }) {
                    SettingsSearchResult(
                        target = baseTarget.copy(fieldCode = field.code),
                        groupName = section.group.displayName,
                        modelName = modelName,
                        fieldName = field.name,
                    )
                } else {
                    null
                }
            }

            if (modelMatches) {
                listOf(
                    SettingsSearchResult(
                        target = baseTarget,
                        groupName = section.group.displayName,
                        modelName = modelName,
                        fieldName = null,
                    ),
                ) + fieldResults
            } else {
                fieldResults
            }
        }
    }
}

private data class ParsedSectionName(
    val prefix: String,
    val remainder: String,
)

private fun parseSectionName(rawName: String): ParsedSectionName? {
    val separatorIndex = rawName.indexOfFirst { it == '|' || it == '｜' }
    if (separatorIndex < 0) return null

    val prefix = rawName.substring(0, separatorIndex).trim()
    val remainder = rawName.substring(separatorIndex + 1).trim()
    if (prefix.isEmpty() || remainder.isEmpty()) return null
    return ParsedSectionName(prefix = prefix, remainder = remainder)
}
