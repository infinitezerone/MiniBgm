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
            defaultConfig.versionCode = 1
            defaultConfig.versionName = "0.1.1"
        }
        configureCoreLibraryDesugaring()
        configureSpotless()
    }
}
