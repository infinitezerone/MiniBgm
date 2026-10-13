import com.infinitezerone.minibgm.androidLibrary
import com.infinitezerone.minibgm.configureAndroidUnitTests

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.user"
    resourcePrefix = "feature_user_"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
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
