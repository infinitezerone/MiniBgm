import com.infinitezerone.minibgm.kmpAndroidLibrary

plugins {
    alias(libs.plugins.minibgm.kmp.library)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        // androidHostTest 源集由 AGP KMP 插件在 finalizeDsl 阶段按需创建，
        // 用 matching+configureEach 惰性匹配，避免脚本求值期源集尚不存在
        matching { it.name == "androidHostTest" }.configureEach {
            dependencies {
                implementation(project(":core:testing"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.androidx.datastore)
            }
        }
    }
}
