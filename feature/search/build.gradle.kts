import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.search"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
