package com.infinitezerone.minibgm

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 内置 Kotlin：不再应用 org.jetbrains.kotlin.android，
        // jvmTarget 默认取 compileOptions.targetCompatibility
        with(pluginManager) {
            apply("com.android.library")
        }

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // lint 增量门禁：存量问题封存于各模块 lint-baseline.xml，CI 只拦新增（error 级失败）
            lint {
                baseline = file("lint-baseline.xml")
            }
        }
        configureCoreLibraryDesugaring()
        configureSpotless()
    }
}
