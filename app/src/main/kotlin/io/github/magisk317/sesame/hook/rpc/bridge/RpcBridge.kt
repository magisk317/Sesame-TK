package io.github.magisk317.sesame.hook.rpc.bridge

import io.github.magisk317.sesame.entity.RpcEntity

/** Transport boundary for Alipay RPC implementations. */
interface RpcBridge {
    fun getVersion(): RpcVersion
    @Throws(Exception::class)
    fun load()
    fun unload()

    fun requestString(entity: RpcEntity?, tryCount: Int, retryInterval: Int): String?
    fun requestObject(entity: RpcEntity?, tryCount: Int, retryInterval: Int): RpcEntity?

    fun requestString(entity: RpcEntity?): String? = requestString(entity, 3, -1)

    fun requestString(method: String?, data: String?): String? = requestString(method, data, 3, 1500)

    fun requestString(method: String?, data: String?, relation: String?): String? =
        requestString(method, data, relation, 3, 1500)

    fun requestString(
        method: String?,
        data: String?,
        appName: String?,
        methodName: String?,
        facadeName: String?,
    ): String? = requestString(RpcEntity(method, data, null, appName, methodName, facadeName), 3, -1)

    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String? =
        requestString(RpcEntity(method, data), tryCount, retryInterval)

    fun requestString(
        method: String?,
        data: String?,
        relation: String?,
        tryCount: Int,
        retryInterval: Int,
    ): String? = requestString(RpcEntity(method, data, relation), tryCount, retryInterval)

    fun requestObject(method: String?, data: String?, relation: String?): RpcEntity? =
        requestObject(method, data, relation, 3, -1)

    fun requestObject(method: String?, data: String?, tryCount: Int, retryInterval: Int): RpcEntity? =
        requestObject(RpcEntity(method, data), tryCount, retryInterval)

    fun requestObject(
        method: String?,
        data: String?,
        relation: String?,
        tryCount: Int,
        retryInterval: Int,
    ): RpcEntity? = requestObject(RpcEntity(method, data, relation), tryCount, retryInterval)
}
