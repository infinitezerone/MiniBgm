import com.infinitezerone.minibgm.androidLibrary
import com.infinitezerone.minibgm.configureAndroidUnitTests

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

// 测试 JVM / Roborazzi 录制 / 截图测试跳过开关的单一真源（build-logic 的 AndroidUnitTest.kt）
configureAndroidUnitTests()
