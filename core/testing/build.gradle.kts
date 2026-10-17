import com.infinitezerone.minibgm.kmpAndroidLibrary

plugins {
    alias(libs.plugins.minibgm.kmp.library)
    alias(libs.plugins.minibgm.crap.check)
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
            implementation(libs.kotlinx.serialization.json)
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
    inputs.files(rootProject.fileTree("feature") { include("*/src/**", "*/build.gradle.kts", "*.gradle.kts") })
    inputs.files(rootProject.fileTree("core") { include("*/src/**", "*/build.gradle.kts", "*.gradle.kts") })
    // 架构红线里有一条直接读仓库根的 AGENTS.md（上下文预算守卫）。不把它声明成任务输入的话，
    // 「只改文档」不会让红线重跑——预算守卫会变成永远绿的摆设（本地实测过：UP-TO-DATE 直接跳过）
    inputs.file(rootProject.file("AGENTS.md")).withPropertyName("agentsDoc")
    inputs.files(rootProject.fileTree(".agents/skills") { include("**/*.md") }).withPropertyName("skillDocs")
}
