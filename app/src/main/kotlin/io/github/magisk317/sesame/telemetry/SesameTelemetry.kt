package io.github.magisk317.sesame.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.entitlement.EntitlementGate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.net.URI
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Anonymous Sesame success telemetry. It is inert until a server lease is saved. */
object SesameTelemetry {
    private const val PREFS = "sesame-telemetry"
    // Keep the entitlement library's key and token storage contract so telemetry
    // is authenticated by the same registered device key.
    private const val KEY_ALIAS = "mobile-entitlement-p256-v1"
    private const val KEY_EVENTS = "events"
    private const val KEY_SNAPSHOTS = "snapshots"
    private const val KEY_DAILY_ENERGY = "daily_energy"
    private const val KEY_DAILY_ENERGY_DAY = "daily_energy_day"
    private const val QUEUE_FILE = "sesame-telemetry.json"
    // The API worker is separate from the public Pages dashboard.
    private const val ENDPOINT = "https://activate.magisk317.qzz.io/v1/sesame/telemetry"
    private const val MAX_PENDING = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private var prefs: SharedPreferences? = null
    private var uploaderProcess = false

    @JvmStatic
    fun initialize(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            val appContext = context.applicationContext
            prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            uploaderProcess = appContext.packageName == General.MODULE_PACKAGE_NAME
            ensureKeyPair()
            migrateLegacyQueue()
        }
        flush()
    }

    fun recordEnergyCollected(grams: Int) {
        if (grams <= 0) return
        recordEvent("forest_energy_collected", grams)
        val currentPrefs = prefs ?: return
        val today = System.currentTimeMillis() / 86_400_000L
        synchronized(lock) {
            val previousDay = currentPrefs.getLong(KEY_DAILY_ENERGY_DAY, Long.MIN_VALUE)
            val total = if (previousDay == today) currentPrefs.getInt(KEY_DAILY_ENERGY, 0) + grams else grams
            currentPrefs.edit().putLong(KEY_DAILY_ENERGY_DAY, today).putInt(KEY_DAILY_ENERGY, total).apply()
            recordSnapshot("forest_energy_collected_grams", total)
        }
    }

    fun recordEnergyRanking(value: Int) {
        if (value >= 0) recordSnapshot("forest_energy_ranking_value", value)
    }

    fun readEntitlement(): String? = EntitlementGate.readEntitlementToken()

    fun flushPending() = flush()

    /** Reads the server-issued logical device identifier for display only. */
    fun readEntitlementDeviceId(): String? = runCatching {
        val payload = readEntitlement()?.split('.')?.getOrNull(1) ?: return null
        val jsonObject = json.parseToJsonElement(
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8),
        ).jsonObject
        jsonObject["device_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clearLocalData(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        clearQueue()
    }

    fun recordSnapshot(name: String, value: Int) {
        enqueue(KEY_SNAPSHOTS, buildJsonObject {
            put("metric_name", JsonPrimitive(name))
            put("metric_value", JsonPrimitive(value))
            put("captured_at", JsonPrimitive(System.currentTimeMillis() / 1000))
        })
    }

    fun recordEvent(name: String, value: Int = 1) {
        enqueue(KEY_EVENTS, buildJsonObject {
            put("event_id", JsonPrimitive(UUID.randomUUID().toString()))
            put("event_name", JsonPrimitive(name))
            put("event_value", JsonPrimitive(value))
            put("occurred_at", JsonPrimitive(System.currentTimeMillis() / 1000))
        })
    }

    private fun enqueue(key: String, item: JsonObject) {
        if (prefs == null) return
        updateQueue { root ->
            val current = root[key] as? JsonArray ?: JsonArray(emptyList())
            val next = buildJsonArray {
                current.takeLast(MAX_PENDING - 1).forEach(::add)
                add(item)
            }
            buildJsonObject {
                root.forEach { (name, value) -> if (name != key) put(name, value) }
                put(key, next)
            }
        }
        flush()
    }

    private fun migrateLegacyQueue() {
        val currentPrefs = prefs ?: return
        val snapshots = readArray(currentPrefs.getString(KEY_SNAPSHOTS, null))
        val events = readArray(currentPrefs.getString(KEY_EVENTS, null))
        if (snapshots.isEmpty() && events.isEmpty()) return
        updateQueue { root ->
            buildJsonObject {
                root.forEach { (name, value) -> put(name, value) }
                put(KEY_SNAPSHOTS, mergeQueue(root[KEY_SNAPSHOTS] as? JsonArray, snapshots))
                put(KEY_EVENTS, mergeQueue(root[KEY_EVENTS] as? JsonArray, events))
            }
        }
        currentPrefs.edit().remove(KEY_SNAPSHOTS).remove(KEY_EVENTS).apply()
    }

    private fun mergeQueue(existing: JsonArray?, incoming: JsonArray): JsonArray = buildJsonArray {
        (existing ?: JsonArray(emptyList())).plus(incoming)
            .takeLast(MAX_PENDING)
            .forEach(::add)
    }

    private fun flush() {
        if (prefs == null || !uploaderProcess) return
        val entitlement = readEntitlement().orEmpty()
        if (entitlement.isBlank()) return
        GlobalThreadPools.execute {
            runCatching { upload(entitlement) }
        }
    }

    private fun upload(entitlement: String) {
        val root = readQueue()
        val snapshots = root[KEY_SNAPSHOTS] as? JsonArray ?: JsonArray(emptyList())
        val events = root[KEY_EVENTS] as? JsonArray ?: JsonArray(emptyList())
        if (snapshots.isEmpty() && events.isEmpty()) return
        val body = buildJsonObject {
            put("entitlement", JsonPrimitive(entitlement))
            put("dpop", JsonPrimitive(createDpop()))
            put("snapshots", snapshots)
            put("events", events)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            updateQueue { current ->
                buildJsonObject {
                    current.forEach { (name, value) ->
                        if (name != KEY_SNAPSHOTS && name != KEY_EVENTS) put(name, value)
                    }
                    put(KEY_SNAPSHOTS, buildJsonArray {
                        ((current[KEY_SNAPSHOTS] as? JsonArray) ?: JsonArray(emptyList()))
                            .filterNot { it in snapshots }.forEach(::add)
                    })
                    put(KEY_EVENTS, buildJsonArray {
                        ((current[KEY_EVENTS] as? JsonArray) ?: JsonArray(emptyList()))
                            .filterNot { it in events }.forEach(::add)
                    })
                }
            }
        }
    }

    private fun queueFile(): File = File(Files.MAIN_DIR, QUEUE_FILE)

    private fun readQueue(): JsonObject = withQueue { root -> root }

    private fun updateQueue(transform: (JsonObject) -> JsonObject) {
        Files.ensureDir(Files.MAIN_DIR)
        channelFor(queueFile()).use { channel ->
            channel.lock().use {
                val root = readQueueFromChannel(channel)
                val updated = transform(root)
                val bytes = json.encodeToString(JsonElement.serializer(), updated)
                    .toByteArray(StandardCharsets.UTF_8)
                channel.truncate(0)
                channel.position(0)
                channel.write(ByteBuffer.wrap(bytes))
                channel.force(false)
            }
        }
    }

    private fun <T> withQueue(block: (JsonObject) -> T): T {
        Files.ensureDir(Files.MAIN_DIR)
        val file = queueFile()
        return channelFor(file).use { channel ->
            channel.lock().use {
                block(readQueueFromChannel(channel))
            }
        }
    }

    private fun readQueueFromChannel(channel: FileChannel): JsonObject {
        channel.position(0)
        val bytes = ByteArray(channel.size().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        var offset = 0
        while (offset < bytes.size) {
            val read = channel.read(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            if (read <= 0) break
            offset += read
        }
        return runCatching {
            json.parseToJsonElement(String(bytes, 0, offset, StandardCharsets.UTF_8)).jsonObject
        }.getOrDefault(JsonObject(emptyMap()))
    }

    private fun channelFor(file: File): FileChannel = FileChannel.open(
        file.toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
    )

    private fun clearQueue() {
        runCatching { queueFile().delete() }
    }

    private fun readArray(value: String?): JsonArray = runCatching {
        json.parseToJsonElement(value.orEmpty()) as? JsonArray ?: JsonArray(emptyList())
    }.getOrDefault(JsonArray(emptyList()))

    private fun ensureKeyPair() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) return
        val generator = java.security.KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN,
        ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build())
        generator.generateKeyPair()
    }

    private fun keyPair(): Pair<ECPublicKey, PrivateKey> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getCertificate(KEY_ALIAS).publicKey as ECPublicKey) to
            (store.getKey(KEY_ALIAS, null) as PrivateKey)
    }

    private fun publicJwk(): JsonObject {
        val publicKey = keyPair().first
        return buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive(base64Url(unsigned(publicKey.w.affineX, 32))))
            put("y", JsonPrimitive(base64Url(unsigned(publicKey.w.affineY, 32))))
        }
    }

    private fun createDpop(): String {
        val header = buildJsonObject {
            put("alg", JsonPrimitive("ES256"))
            put("typ", JsonPrimitive("dpop+jwt"))
            put("jwk", publicJwk())
        }.toString()
        val payload = jsonObject(
            "htu" to URI(ENDPOINT).let { "${it.scheme}://${it.authority}${it.path}" },
            "htm" to "POST",
            "iat" to System.currentTimeMillis() / 1000,
            "jti" to UUID.randomUUID().toString(),
        ).toString()
        val signingInput = "${base64Url(header.toByteArray())}.${base64Url(payload.toByteArray())}"
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair().second)
            update(signingInput.toByteArray())
        }.sign()
        return "$signingInput.${base64Url(derToJose(signature, 32))}"
    }

    private fun derToJose(der: ByteArray, size: Int): ByteArray {
        var offset = 2
        if ((der[1].toInt() and 0xff) > 0x80) offset++
        val rLength = der[offset + 1].toInt() and 0xff
        val r = der.copyOfRange(offset + 2, offset + 2 + rLength)
        offset += 2 + rLength + 2
        val sLength = der[offset - 1].toInt() and 0xff
        val s = der.copyOfRange(offset, offset + sLength)
        return unsigned(r, size) + unsigned(s, size)
    }

    private fun unsigned(value: BigInteger, size: Int): ByteArray {
        return unsigned(value.toByteArray(), size)
    }

    private fun unsigned(source: ByteArray, size: Int): ByteArray {
        val result = ByteArray(size)
        source.copyInto(result, destinationOffset = (size - source.size).coerceAtLeast(0), startIndex = (source.size - size).coerceAtLeast(0))
        return result
    }

    private fun base64Url(value: ByteArray): String = android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
}
