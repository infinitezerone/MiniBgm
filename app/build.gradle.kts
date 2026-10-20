import com.infinitezerone.minibgm.androidApplication
import com.infinitezerone.minibgm.configureAndroidUnitTests
import com.infinitezerone.minibgm.nativeAbis
import java.util.Properties

// keystore.properties 不入库：本机没有该文件时 release 回退 debug 签名，保证任何人都能构建
val releaseSigning =
    Properties().apply {
        rootProject
            .file("keystore.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

plugins {
    alias(libs.plugins.minibgm.android.application.compose)
    alias(libs.plugins.kotlin.serialization)
}

// local.properties 不入库：本机开发或 CI 构建时注入私有网关与端点（OAuth 换票代理、弹弹play 缓存网关）
val localProps =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

fun resolveProperty(
    key: String,
    envKey: String,
    defaultValue: String = "",
): String =
    (project.findProperty(key) as? String)
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: defaultValue

val tier = (project.findProperty("minibgm.tier") as? String) ?: "preview"
val isPreview = tier == "preview"
val enableAi = (project.findProperty("minibgm.enableAi") as? String)?.toBoolean() ?: true
val enablePlayer = (project.findProperty("minibgm.enablePlayer") as? String)?.toBoolean() ?: true
val oauthProxyUrl = resolveProperty("minibgm.oauthProxyUrl", "MINIBGM_OAUTH_PROXY_URL", "")
val danmakuEndpoint = resolveProperty("minibgm.danmakuEndpoint", "MINIBGM_DANMAKU_ENDPOINT", "")

// 原生库 ABI 唯一真源（native/abis.txt）：ndk.abiFilters 与 splits.abi 均由此派生
val nativeLibAbis = nativeAbis()

// ABI 分包默认**关闭**：debug / 日常构建只产出 1 个 APK。
// 开启时 AGP 会为每个 ABI（native/abis.txt：arm64-v8a + x86_64）再各打一个包外加 universal 包，
// 实测 debug 产出 3 个 APK 共 363MB 写盘——这是每次 assembleDebug（含每次 jgate）都在付的重复开销。
// 发版必须产出分 ABI 产物，由 .github/workflows/release.yml 显式传 -Pminibgm.abiSplits=true 开启。
val enableAbiSplits = (project.findProperty("minibgm.abiSplits") as? String)?.toBoolean() ?: false

androidApplication {
    namespace = "com.infinitezerone.minibgm"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    defaultConfig {
        applicationId = "com.infinitezerone.minibgm"
        buildConfigField("Boolean", "ENABLE_AI_ASSISTANT", enableAi.toString())
        buildConfigField("Boolean", "ENABLE_INTERNAL_PLAYER", enablePlayer.toString())
        buildConfigField("String", "BUILD_TIER", "\"$tier\"")
        buildConfigField("String", "OAUTH_TOKEN_PROXY_URL", "\"$oauthProxyUrl\"")
        buildConfigField("String", "DANMAKU_DEFAULT_ENDPOINT", "\"$danmakuEndpoint\"")
        ndk {
            abiFilters.addAll(nativeLibAbis)
        }
    }

    signingConfigs {
        if (releaseSigning.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig =
                if (releaseSigning.isNotEmpty()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = enableAbiSplits
            reset()
            include(*nativeLibAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:webview"))
    implementation(project(":feature:user"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:subject"))
    implementation(project(":feature:search"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:widget"))
    implementation(project(":sync:work"))

    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.gif)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(project(":core:testing"))
}

configureAndroidUnitTests()
