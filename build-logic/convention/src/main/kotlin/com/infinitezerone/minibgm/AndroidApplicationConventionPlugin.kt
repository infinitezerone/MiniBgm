package com.infinitezerone.minibgm

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 内置 Kotlin：不再应用 org.jetbrains.kotlin.android，
        // jvmTarget 默认取 compileOptions.targetCompatibility
        with(pluginManager) {
            apply("com.android.application")
        }

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            // 版本注入（CI/发布流程约定）：
            // - tag 发布时由 release workflow 传 -Pminibgm.versionName=<tag 去掉 v 前缀>
            //   与 -Pminibgm.versionCode=<major*10000+minor*100+patch>，tag 是版本的唯一事实源；
            // - 本地构建无属性时回退到此处默认值，保证任何人都能构建。
            // 修改默认值 = 准备一次新版本；versionCode 必须严格递增（Android 升级安装约束）。
            defaultConfig.versionCode =
                (providers.gradleProperty("minibgm.versionCode").orNull)?.toInt() ?: 16
            defaultConfig.versionName =
                providers.gradleProperty("minibgm.versionName").orNull ?: "0.2.8"
        }
        configureCoreLibraryDesugaring()
        configureSpotless()
    }
}
