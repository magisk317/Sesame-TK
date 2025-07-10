package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DeviceInfoUtil {

    @Composable
    fun DeviceInfoCard(info: Map<String, String>) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                info.forEach { (label, value) ->
                    Text(text = "$label: $value", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }


    @SuppressLint("HardwareIds")
    fun getDeviceInfo(context: Context): Map<String, String> {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        fun getProp(prop: String): String {
            return try {
                val p = Runtime.getRuntime().exec("getprop $prop")
                p.inputStream.bufferedReader().readLine().orEmpty()
            } catch (e: Exception) {
                "未知"
            }
        }

        fun runAsRoot(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                process.inputStream.bufferedReader().readText().trim()
            } catch (e: Exception) {
                "Root命令失败：" + e.message
            }
        }


        fun getMarketName(): String {
            val candidates = listOf(
                "ro.vendor.oplus.marketname",     // 一加 / OPPO
                "ro.product.marketname",          // 小米 / vivo 等
                "ro.product.name",
                "ro.product.model"
            )
            for (prop in candidates) {
                val value = getProp(prop)
                if (value.isNotBlank()) return value
            }
            return "${Build.BRAND} ${Build.MODEL}"
        }

        // 获取设备序列号，注意Android10+环境下此方法需要系统权限
        val sn = try {
            runAsRoot("getprop ro.serialno")
        } catch (_: Exception) {
            "受限/不可用"
        }

        // 获取IMEI或设备ID, Android10+使用时需系统签名或MDM权限，否则为 "受限/不可用"
        val imei = try {
            runAsRoot("service call iphonesubinfo 1")
        } catch (_: Exception) {
            "受限/不可用"
        }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        return mapOf(
            "型号" to Build.MODEL,
            "产品" to getMarketName(),
            "Android ID" to androidId,
            "系统" to "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            "构建" to "${Build.ID} ${Build.DISPLAY}",
            "OTA" to getProp("ro.build.version.ota"),
            "SN" to sn,
//            "IMEI" to imei
        )
    }
}
