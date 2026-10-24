# ---------- 框架 ----------
-keep class de.robv.android.xposed.** { *; }
-keep class io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.service.**

# ---------- Shizuku ----------
-keep class dev.rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**

# ---------- cmd-android ----------
-keep class com.niki.** { *; }
-dontwarn com.niki.**


# ---------- 日志 ----------
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn ch.qos.logback.**, org.slf4j.**

# ---------- 本工程 ----------
# 包名沿用上游规则语义：保留整个模块包。入口类 io.github.magisk317.sesame.xp.LibXposedEntry
# 仅被 META-INF/xposed/java_init.list 资源反射引用，若无此规则会被 R8 剥离，
# 导致 LSPosed 报 ClassNotFoundException、hook 无法加载（2026-09-05 事故）。
-keep class io.github.magisk317.sesame.** { *; }
-keep class io.github.magisk317.xposed.** { *; }

# ---------- Jackson（最小必要） ----------
-keepattributes Signature, *Annotation*
-keepclassmembers class * {
}

# ---------- 序列化 & 缺失类 ----------
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable { *; }
-dontwarn java.beans.ConstructorProperties, java.beans.Transient
