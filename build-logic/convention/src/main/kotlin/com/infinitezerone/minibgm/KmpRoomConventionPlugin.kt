package com.infinitezerone.minibgm

import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("androidx.room3")
            apply("com.google.devtools.ksp")
        }

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        val roomCompiler = libs.findLibrary("androidx-room-compiler").get()

        dependencies {
            add("kspAndroid", roomCompiler)
            // add("kspJvm", roomCompiler) // 桌面端暂未启用
        }

        // AGP 的 host-test lint 任务会读取 KSP 生成目录，但当前插件组合未自动建立任务边。
        val kspAndroidHostTest = tasks.matching { it.name == "kspAndroidHostTest" }
        tasks.matching {
            it.name == "generateAndroidHostTestLintModel" || it.name == "lintAnalyzeAndroidHostTest"
        }.configureEach {
            dependsOn(kspAndroidHostTest)
        }
    }
}
