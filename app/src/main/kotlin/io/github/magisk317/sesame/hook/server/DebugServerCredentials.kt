package io.github.magisk317.sesame.hook.server

import io.github.magisk317.sesame.service.HostRuntimeStore
import java.security.SecureRandom

/**
 * Debug HTTP 服务器的 per-install 凭据。
 *
 * 原实现把一个写死的字符串 "ET3vB^#td87sQqKaY*eMUJXP" 编译进 APK，由于本模块是公开
 * Xposed 模块，任何人都能反编译拿到 token，等同于 debug 服务器无认证。这里改为每次
 * 安装生成随机 token 并持久化到 Hook 进程私有的 [HostRuntimeStore]，debug 服务器自产自读。
 * 配合 [ModuleHttpServer] 仅绑定 127.0.0.1，token 不写入共享外部文件。
 */
internal object DebugServerCredentials {
    private const val KEY_TOKEN = "debug_server_token"
    private const val TOKEN_BYTE_LENGTH = 32

    /**
     * 返回当前安装的 token；若不存在则生成并持久化一个随机 token 后返回。
     * 失败时返回空串，调用方应据此放弃启动服务器（fail-closed）。
     */
    fun getOrCreate(): String {
        HostRuntimeStore.getString(HostRuntimeStore.DEBUG_SERVER_TOKEN_KEY)?.let { return it }

        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toBase64Url()
        check(HostRuntimeStore.putString(HostRuntimeStore.DEBUG_SERVER_TOKEN_KEY, token)) {
            "private debug token storage unavailable"
        }
        return token
    }

    private fun ByteArray.toBase64Url(): String {
        // 使用 URL-safe base64，避免 +/= 在 HTTP header 中需要转义。
        return android.util.Base64.encodeToString(
            this,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }
}
