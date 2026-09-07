import com.infinitezerone.minibgm.kmpAndroidLibrary

plugins {
    alias(libs.plugins.minibgm.kmp.library)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.testing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:data"))
            api(project(":core:datastore"))
            api(libs.androidx.datastore.core)
            api(libs.kotlinx.coroutines.test)
            api(libs.kotlin.test)
            api(libs.junit)
        }
        matching { it.name == "androidHostTest" }.configureEach {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.junit)
            }
        }
    }
}

tasks.matching { it.name.startsWith("testAndroid") }.configureEach {
    inputs.files(rootProject.fileTree("feature") { include("*/src/**", "*.gradle.kts") })
    inputs.files(rootProject.fileTree("core") { include("*/src/**", "*.gradle.kts") })
}
