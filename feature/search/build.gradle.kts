import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.search"
    resourcePrefix = "feature_search_"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
