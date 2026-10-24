package io.github.magisk317.sesame.task.AnswerAI

import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.model.modelFieldExt.TextModelField
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

class AnswerAI : Model() {
    private var enabled = false
    private var activeType = AIType.TONGYI
    private var answerAIInterface: AnswerAIInterface = AnswerAIInterface.getInstance()

    private val aiType = ChoiceModelField("useGeminiAI", "AI类型", AIType.TONGYI, AIType.nickNames)
    private val getTongyiAIToken = TextModelField.UrlTextModelField(
        "getTongyiAIToken", "通义千问 | 获取令牌",
        "https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key",
    )
    private val tongYiToken = StringModelField("tongYiToken", "qwen-turbo | 设置令牌", "")
    private val getGeminiAIToken = TextModelField.UrlTextModelField(
        "getGeminiAIToken", "Gemini | 获取令牌", "https://aistudio.google.com/app/apikey",
    )
    private val geminiToken = StringModelField("GeminiAIToken", "gemini-1.5-flash | 设置令牌", "")
    private val getDeepSeekToken = TextModelField.UrlTextModelField(
        "getDeepSeekToken", "DeepSeek | 获取令牌", "https://platform.deepseek.com/usage",
    )
    private val deepSeekToken = StringModelField("DeepSeekToken", "DeepSeek-R1 | 设置令牌", "")
    private val getCustomServiceToken = TextModelField.ReadOnlyTextModelField(
        "getCustomServiceToken", "粉丝福利😍", "感谢 Summer 提供公益 API",
    )
    private val customServiceToken = StringModelField("CustomServiceToken", "自定义服务 | 设置令牌", "")
    private val customServiceUrl = StringModelField(
        "CustomServiceBaseUrl", "自定义服务 | 设置BaseUrl", "https://api.siliconflow.cn/v1",
    )
    private val customServiceModel = StringModelField(
        "CustomServiceModel", "自定义服务 | 设置模型", "deepseek-ai/DeepSeek-V3",
    )

    override fun getName(): String = "AI答题"

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = "AnswerAI.svg"

    override fun getFields(): ModelFields = ModelFields().apply {
        addField(aiType)
        addField(getTongyiAIToken)
        addField(tongYiToken)
        addField(getGeminiAIToken)
        addField(geminiToken)
        addField(getDeepSeekToken)
        addField(deepSeekToken)
        addField(getCustomServiceToken)
        addField(customServiceToken)
        addField(customServiceUrl)
        addField(customServiceModel)
    }

    override fun boot(classLoader: ClassLoader?) {
        try {
            enabled = getEnableField().value
            activeType = aiType.value
            initializeAIService(activeType)
            activeModel = this
            StructuredLog.record("初始化AI服务：已选择[${AIType.nickNames[aiType.value]}]")
        } catch (error: Throwable) {
            StructuredLog.error(TAG, "初始化AI服务失败: ${error.message}")
            StructuredLog.printStackTrace(TAG, error)
        }
    }

    override fun destroy() {
        answerAIInterface.release()
        answerAIInterface = AnswerAIInterface.getInstance()
        enabled = false
        if (activeModel === this) activeModel = null
    }

    private fun initializeAIService(selectedType: Int) {
        answerAIInterface.release()
        answerAIInterface = when (selectedType) {
            AIType.TONGYI -> TongyiAI(tongYiToken.value)
            AIType.GEMINI -> GeminiAI(geminiToken.value)
            AIType.DEEPSEEK -> DeepSeek(deepSeekToken.value)
            AIType.CUSTOM -> CustomService(customServiceToken.value, customServiceUrl.value).also {
                it.setModelName(customServiceModel.value)
                StructuredLog.record("已配置自定义服务：URL=[${customServiceUrl.value}], Model=[${customServiceModel.value}]")
            }
            else -> AnswerAIInterface.getInstance()
        }
    }

    companion object {
        private const val TAG = "AnswerAI"
        private const val QUESTION_LOG_FORMAT = "题目📒 [%s] | 选项: %s"
        private const val AI_ANSWER_LOG_FORMAT = "AI回答🧠 [%s] | AI类型: [%s] | 模型名称: [%s]"
        private const val NORMAL_ANSWER_LOG_FORMAT = "普通回答🤖 [%s]"
        private const val ERROR_AI_ANSWER = "AI回答异常：无法获取有效答案，请检查AI服务配置是否正确"

        @Volatile
        private var activeModel: AnswerAI? = null

        object AIType {
            const val TONGYI = 0
            const val GEMINI = 1
            const val DEEPSEEK = 2
            const val CUSTOM = 3
            val nickNames = arrayOf("通义千问", "Gemini", "DeepSeek", "自定义")
        }

        @JvmStatic
        fun getAnswer(text: String?, answerList: List<String>?, flag: String?): String {
            if (text == null || answerList == null) {
                selectLogger(flag, "问题或答案列表为空")
                return ""
            }
            return try {
                selectLogger(flag, QUESTION_LOG_FORMAT.format(text, answerList))
                val model = activeModel
                if (model?.enabled == true) {
                    val answerIndex = model.answerAIInterface.getAnswer(text, answerList) ?: -1
                    if (answerIndex in answerList.indices) {
                        answerList[answerIndex].also {
                            selectLogger(flag, AI_ANSWER_LOG_FORMAT.format(it, AIType.nickNames[model.activeType], model.answerAIInterface.getModelName()))
                        }
                    } else {
                        StructuredLog.error(ERROR_AI_ANSWER)
                        ""
                    }
                } else {
                    answerList.firstOrNull().orEmpty().also {
                        if (it.isNotEmpty()) selectLogger(flag, NORMAL_ANSWER_LOG_FORMAT.format(it))
                    }
                }
            } catch (error: Throwable) {
                StructuredLog.printStackTrace(TAG, "AI获取答案异常:", error)
                ""
            }
        }

        private fun selectLogger(flag: String?, message: String) {
            when (flag) {
                "farm" -> StructuredLog.farm(message)
                "forest" -> StructuredLog.forest(message)
                else -> StructuredLog.other(message)
            }
        }
        init {
            ModelOrder.register(ModelDefinition(AnswerAI::class.java) { AnswerAI() })
        }
    }
}
