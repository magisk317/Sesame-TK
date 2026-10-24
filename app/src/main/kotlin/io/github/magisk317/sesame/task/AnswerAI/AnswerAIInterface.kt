package io.github.magisk317.sesame.task.AnswerAI

interface AnswerAIInterface {
    fun setModelName(modelName: String?) = Unit

    fun getModelName(): String = ""

    fun getAnswerStr(text: String): String

    fun getAnswerStr(text: String, model: String): String

    fun getAnswer(title: String, answerList: List<String>): Int?

    fun release() = Unit

    companion object {
        @JvmStatic
        fun getInstance(): AnswerAIInterface = Default.INSTANCE
    }

    private object Default : AnswerAIInterface {
        val INSTANCE: AnswerAIInterface = this

        override fun getAnswerStr(text: String): String = ""

        override fun getAnswerStr(text: String, model: String): String = ""

        override fun getAnswer(title: String, answerList: List<String>): Int? = -1
    }
}
