import com.infinitezerone.minibgm.kmpAndroidLibrary

plugins {
    alias(libs.plugins.minibgm.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.ai"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:data"))

            implementation(libs.koog.agents)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}
