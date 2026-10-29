import com.infinitezerone.minibgm.kmpAndroidLibrary
import com.infinitezerone.minibgm.registerPrebuiltJniLibs

plugins {
    alias(libs.plugins.minibgm.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.network"
}

registerPrebuiltJniLibs(
    buildSubPath = "ech-native/jniLibs",
    libraryFileName = "libminibgm_ech.so",
    sourceDir = "crates/minibgm-ech",
)

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.encoding)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        // androidHostTest 源集由 AGP KMP 插件在 finalizeDsl 阶段按需创建，
        // 用 matching+configureEach 惰性匹配，避免脚本求值期源集尚不存在
        matching { it.name == "androidHostTest" }.configureEach {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
