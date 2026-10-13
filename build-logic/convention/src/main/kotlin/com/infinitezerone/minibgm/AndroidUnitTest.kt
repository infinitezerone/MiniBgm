package com.infinitezerone.minibgm

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Android 单元测试统一配置（被 Robolectric / Roborazzi 截图测试的模块共用）。
 *
 * 三件事：
 * 1. 测试 JVM 固定 Java 21——与 `compileOptions` 的 21 对齐，避免 Robolectric NATIVE 图形栈
 *    在不同 JVM 上行为漂移；
 * 2. `-Proborazzi.record=true`（或 `roborazzi.test.record`）时开启截图录制；
 * 3. `-Pminibgm.skipScreenshots=true` 时跳过截图测试——**默认不跳过**。
 *
 * 为什么有第 3 项：截图测试实测占全部单测耗时的 43%（59s / 137s，仅 15 例；Robolectric
 * NATIVE 图形栈每个类都要冷启动一次），是本地 16GB 机器上最大的一块测试开销。默认仍然全跑
 * （本地绿 == CI 绿），只在显式传属性时跳过，并在构建输出里打印醒目提示，避免把"跳过"
 * 误读成"通过"。
 *
 * 注意：本函数必须是 public——模块 build.gradle.kts 直接调用它（同 `androidLibrary`）。
 */
fun Project.configureAndroidUnitTests() {
    val skipScreenshots = providers.gradleProperty("minibgm.skipScreenshots").orNull == "true"
    val isRecord =
        providers.gradleProperty("roborazzi.record").orNull == "true" ||
            providers.gradleProperty("roborazzi.test.record").orNull == "true"

    tasks.withType<Test>().configureEach {
        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            },
        )
        if (isRecord) {
            systemProperty("roborazzi.test.record", "true")
        }
        if (skipScreenshots) {
            filter {
                excludeTestsMatching("*ScreenshotTest")
                isFailOnNoMatchingTests = false
            }
            doFirst {
                logger.lifecycle(
                    "[minibgm] ⚠️ 已按 -Pminibgm.skipScreenshots=true 跳过截图测试：" +
                        "本轮不覆盖 Roborazzi 视觉回归（CI 仍然全跑）",
                )
            }
        }
    }
}
