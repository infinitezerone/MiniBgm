import com.infinitezerone.minibgm.androidLibrary
import com.infinitezerone.minibgm.configureAndroidUnitTests
import com.infinitezerone.minibgm.registerPrebuiltJniLibsForLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.subject"
    resourcePrefix = "feature_subject_"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// mpv render API 桥（libminibgm_mpv.so）：由 tools/build-native mpv 预构建
registerPrebuiltJniLibsForLibrary(
    buildSubPath = "mpv-native/jniLibs",
    libraryFileName = "libminibgm_mpv.so",
)

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.libmpv)

    // Roborazzi 面板截图自查（Robolectric NATIVE 图形，纯 JVM 无需设备）
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 测试 JVM / Roborazzi 录制 / 截图测试跳过开关的单一真源（build-logic 的 AndroidUnitTest.kt）
configureAndroidUnitTests()
