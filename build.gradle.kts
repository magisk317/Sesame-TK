buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            // Java 26 bytecode target: the ASM bundled with AGP 9.3.1 cannot
            // read the class files the Kotlin toolchain emits here, and the
            // android-asm-instrumented-jars transform then fails while
            // instrumenting composite dependencies such as magisk-ui-kit.
            // ASM 9.10.1 is forced on this classpath because this is the one
            // AGP actually runs on - project-level forces do not reach it.
            // Same workaround XposedSmsCode uses for its bytecode target.
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
        }
    }
}

plugins {
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    // Pin the KMP plugin versions for the composite build: magisk-ui-kit
    // requests org.jetbrains.kotlin.multiplatform with the shared catalog
    // version, but build-logic pulls kotlin-gradle-plugin onto the
    // composite classpath without a version descriptor - Gradle then
    // refuses the versioned request ("already on the classpath with an
    // unknown version"). Same pinning as XposedSmsCode.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    id("magisk.android.compose") apply false
    id("magisk.maintenance")
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.rikka.tools.refine) apply false
}

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
