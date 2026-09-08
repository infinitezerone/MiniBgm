import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.user"
}

dependencies {
    // UserViewModel 经 Custom Tabs 打开授权页
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)
}
