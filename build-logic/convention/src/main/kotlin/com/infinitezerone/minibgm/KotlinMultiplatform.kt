package com.infinitezerone.minibgm

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        // toolchain 21：对齐 Java 21 LTS 工业界标准，全面兼容 Robolectric 与字节码工具链
        jvmToolchain(21)

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        // jvm() // 桌面端目标（暂未启用，后续需要时开启）

        applyDefaultHierarchyTemplate()
    }

    // Android target 由 com.android.kotlin.multiplatform.library 注册，无法经
    // androidTarget{} 配置 jvmTarget；在编译任务层统一固定为 21，
    // 避免随 toolchain/运行 JDK 漂移导致字节码目标不一致。
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
