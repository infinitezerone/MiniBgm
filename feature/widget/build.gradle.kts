import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.widget"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.core)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
