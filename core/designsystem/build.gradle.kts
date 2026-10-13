import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.library.compose)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.core.designsystem"
    resourcePrefix = "core_designsystem_"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
    val isRecord =
        providers.gradleProperty("roborazzi.record").orNull == "true" ||
            providers.gradleProperty("roborazzi.test.record").orNull == "true"
    if (isRecord) {
        systemProperty("roborazzi.test.record", "true")
    }
}
