package com.infinitezerone.minibgm

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            // AGP KMP 插件必须先于 KGP 应用：AGP 通过 withPlugin("kotlin.multiplatform")
            // 回调注册 Android target，若 KGP 由 KSP 等后续插件触发会导致初始化崩溃
            apply("com.android.kotlin.multiplatform.library")
            apply("org.jetbrains.kotlin.multiplatform")
            apply("org.jetbrains.kotlinx.kover")
        }

        // KotlinMultiplatformAndroidLibraryExtension 未注册为顶层 extension，
        // 只能通过 KotlinMultiplatformAndroidComponentsExtension.finalizeDsl 配置
        extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
            finalizeDsl { androidLibrary ->
                androidLibrary.enableCoreLibraryDesugaring = true
                // 新插件默认关闭 host 测试，显式开启以运行 commonTest / androidHostTest
                androidLibrary.withHostTest { }
            }
        }

        configureCoreLibraryDesugaring()

        configureKotlinMultiplatform()

        configureSpotless()
    }
}
