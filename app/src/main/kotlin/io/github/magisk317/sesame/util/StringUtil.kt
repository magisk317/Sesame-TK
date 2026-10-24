package io.github.magisk317.sesame.util

object StringUtil {
    @JvmStatic
    fun isEmpty(str: String?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun collectionJoinString(conjunction: CharSequence, collection: Collection<*>): String =
        collection.joinToString(conjunction) { it?.toString().orEmpty() }

    @JvmStatic
    fun arrayJoinString(conjunction: CharSequence, vararg array: Any?): String =
        array.joinToString(conjunction) { it?.toString().orEmpty() }

    @JvmStatic
    fun arrayToString(vararg array: Any?): String = arrayJoinString(",", *array)

    @JvmStatic
    fun padLeft(value: Int, totalWidth: Int, padChar: Char): String =
        padLeft(value.toString(), totalWidth, padChar)

    @JvmStatic
    fun padRight(value: Int, totalWidth: Int, padChar: Char): String =
        padRight(value.toString(), totalWidth, padChar)

    @JvmStatic
    fun padLeft(str: String, totalWidth: Int, padChar: Char): String =
        str.padStart(totalWidth, padChar)

    @JvmStatic
    fun padRight(str: String, totalWidth: Int, padChar: Char): String =
        str.padEnd(totalWidth, padChar)

    @JvmStatic
    fun getSubString(text: String, left: String?, right: String?): String {
        val start = if (left.isNullOrEmpty()) 0 else text.indexOf(left).let { index ->
            if (index >= 0) index + left.length else 0
        }
        val end = if (right.isNullOrEmpty()) text.length else text.indexOf(right, start)
        return text.substring(start, if (end >= 0) end else text.length)
    }
}
