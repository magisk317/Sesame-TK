import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    id("magisk.android.application")
    id("magisk.android.compose")
    id("magisk.app.signing")
    id("magisk.app.packaging")
    id("magisk.android.room")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.rikka.tools.refine)
}

// Keep the repository name independent from the user-facing APK artifact name.
extra["artifactBaseName"] = "Sesame"

var isCIBuild: Boolean = System.getenv("CI").toBoolean()
val mobileEntitlementApiOrigin = findProperty("mobileEntitlementApiOrigin")?.toString()
    ?: "https://activate.magisk317.qzz.io"
val mobileEntitlementSigningPublicJwk = findProperty("mobileEntitlementSigningPublicJwk")?.toString()
    ?: """{"kty":"EC","x":"4kPpwUt1wFRuF3EqGq6q57J3YmANf7wyiNH90FNkAbI","y":"U4-E1XK6LjWIXMFNEoSAoik7nD1S07BDb7qAipQd4Ts","crv":"P-256","alg":"ES256","use":"sig","kid":"mobile-entitlement-1"}"""
fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "io.github.magisk317.sesame"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // 使用providers API来支持配置缓存
    val gitCommitCount: Int = providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1

    val gitCommitHash = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
        applicationId = "io.github.magisk317.sesame"

        val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date())

        val buildTime = SimpleDateFormat("HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date())

        versionCode = gitCommitCount

        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "COMMIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("String", "MOBILE_ENTITLEMENT_API_ORIGIN", buildConfigString(mobileEntitlementApiOrigin))
        buildConfigField("String", "MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK", buildConfigString(mobileEntitlementSigningPublicJwk))
        buildConfigField("String", "MOBILE_ENTITLEMENT_CHANNEL", "\"sideload\"")
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf(
            "NotShrinkingResources",
            "VectorRaster",
            "IconLocation",
            "MonochromeLauncherIcon",
            "PrivateResource",
            "Typos",
            "IconXmlAndPng",
            // Sesame is an arm64-only Xposed module for supported Android phones, not ChromeOS.
            "ChromeOsAbiSupport",
        )
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/kotlin")
            jniLibs.directories.add("src/main/jniLibs")
        }
        getByName("test") {
            java.directories.add("src/test/kotlin")
        }
    }

    val cmakeFile = file("src/main/cpp/CMakeLists.txt")
    if (!isCIBuild && cmakeFile.exists()) {
        externalNativeBuild {
            cmake {
                path = cmakeFile
                ndkVersion = "30.0.14904198"
            }
        }
    }

    // magisk.app.packaging defaults to multi-ABI splits; force arm64-only artifacts.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    // Shizuku 相关依赖
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.cmd.android)
    implementation(libs.rikka.refine)

    implementation(libs.ui.text.google.fonts)

    // Compose 额外依赖 (magisk.android.compose 已提供基础)
    implementation(libs.androidx.material.icons.extended)

    // JSON 序列化
    implementation(libs.kotlinx.serialization.json)

    // Kotlin 协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 数据观察和 HTTP 服务
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.nanohttpd)

    // Android 核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.androidx.browser)

    // Xposed 相关依赖
    implementation(project(":magisk-xposed-kit"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    // magisk-ui-kit 共享 UI 组件
    implementation(project(":magisk-ui-kit"))

    // 代码生成和工具库
    implementation(libs.okhttp)
    implementation(libs.dexkit)

    implementation(libs.hiddenapibypass)
    implementation(libs.mobile.entitlement.android)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Jackson JSON
}
