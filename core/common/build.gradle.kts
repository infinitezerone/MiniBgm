import com.infinitezerone.minibgm.kmpAndroidLibrary

plugins {
    alias(libs.plugins.minibgm.kmp.library)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
