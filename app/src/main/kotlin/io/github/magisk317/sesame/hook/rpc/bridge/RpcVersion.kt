package io.github.magisk317.sesame.hook.rpc.bridge

enum class RpcVersion(val code: String) {
    OLD("OLD"),
    NEW("NEW"),
    ;

    companion object {
        private val byCode = entries.associateBy(RpcVersion::code)

        @JvmStatic
        fun getByCode(code: String?): RpcVersion? = byCode[code]
    }
}
