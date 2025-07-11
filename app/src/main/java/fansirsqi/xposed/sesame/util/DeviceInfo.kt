package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.BuildConfig

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class PreviewDeviceInfoProvider : PreviewParameterProvider<Map<String, String>> {
    override val values: Sequence<Map<String, String>> = sequenceOf(
        mapOf(
            "型号" to "Pixel 6",
            "产品" to "Google Pixel",
            "Android ID" to "abcd1234567890ef",
            "系统" to "Android 13 (33)",
            "构建" to "UQ1A.230105.002 S1B51",
            "OTA" to "OTA-12345",
            "SN" to "SN1234567890",
            "模块版本" to "v1.0.0-release 📦",
            "构建日期" to "2023-10-01 12:00 ⏰"
        )
    )
}


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
                when (label) {
                    "Android ID" -> {
                        var showFull by remember { mutableStateOf(false) }
                        val displayValue = if (showFull) value else "***********"
                        Text(
                            text = "$label: $displayValue",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { showFull = !showFull }
                        )
                    }

                    else -> {
                        Text(text = "$label: $value", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

object DeviceInfoUtil {
    @SuppressLint("HardwareIds")
    fun getDeviceInfo(context: Context): Map<String, String> {
        fun getProp(prop: String): String {
            return try {
                val p = Runtime.getRuntime().exec("getprop $prop")
                p.inputStream.bufferedReader().readLine().orEmpty()
            } catch (e: Exception) {
                "未知"
            }
        }

        fun getDeviceName(): String {
            val candidates = listOf(
                "ro.product.marketname",
                "ro.product.odm.device",
                "ro.product.brand"
            )
            for (prop in candidates) {
                val value = getProp(prop)
                if (value.isNotBlank()) return value
            }
            return "${Build.BRAND} ${Build.MODEL}"
        }


        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        return mapOf(
            "Product" to "${Build.MANUFACTURER} ${Build.PRODUCT}",
            "Device" to getDeviceName(),
            "System" to "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            "OS Build" to "${Build.ID} ${Build.DISPLAY}",
            "OTA" to getProp("ro.build.version.ota"),
            "Android ID" to androidId,
            "Module Version" to "${BuildConfig.VERSION}-${BuildConfig.BUILD_TAG}.${BuildConfig.BUILD_TYPE} 📦",
            "Module Build" to "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME} ⏰"
        )
    }
}
