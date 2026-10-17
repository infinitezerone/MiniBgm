import com.infinitezerone.minibgm.kmpAndroidLibrary
import com.infinitezerone.minibgm.nativeAbis
import com.infinitezerone.minibgm.registerPrebuiltJniLibs
import java.security.MessageDigest

plugins {
    alias(libs.plugins.minibgm.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kmpAndroidLibrary {
    namespace = "com.infinitezerone.minibgm.core.data"
}

registerPrebuiltJniLibs(
    buildSubPath = "torrent-native/jniLibs",
    libraryFileName = "libminibgm_torrent.so",
)

// 动态 BT 扩展的唯一信任锚：构建期直接从即将随 APK 分发的预构建 .so 计算哈希。
// 清单进入签名 APK 后，GitHub Release 资产即使被替换也无法通过客户端校验。
val generateNativePluginHashes =
    tasks.register("generateNativePluginHashes") {
        group = "build"
        description = "生成随 APK 分发的 BT 原生扩展 SHA-256 清单"
        dependsOn("verifyPrebuiltJniLibs")

        val abis = nativeAbis()
        // 配置缓存要求任务动作只捕获可序列化值；这里在配置期固化 File，避免把
        // ProjectLayout / Provider 等脚本对象带进 doLast。
        val jniLibsRoot =
            layout.buildDirectory
                .dir("torrent-native/jniLibs")
                .get()
                .asFile
        val outputDir =
            layout.buildDirectory
                .dir("generated/nativePluginHashes")
                .get()
                .asFile
        inputs.property("nativeAbis", abis)
        inputs.files(abis.map { abi -> File(jniLibsRoot, "$abi/libminibgm_torrent.so") })
        outputs.dir(outputDir)

        doLast {
            val outDir = outputDir.apply { mkdirs() }
            val manifestLines =
                abis.map { abi ->
                    val so = File(jniLibsRoot, "$abi/libminibgm_torrent.so")
                    check(so.isFile && so.length() > 0L) {
                        "缺少预构建原生库：${so.absolutePath}，请先运行 tools/build-native"
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    so.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                        }
                    }
                    val hex =
                        digest.digest().joinToString(separator = "") { byte ->
                            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                        }
                    "$abi $hex"
                }

            File(outDir, "native-plugin-sha256.txt").writeText(manifestLines.joinToString("\n", postfix = "\n"))
            File(outDir, "NativePluginHashes.kt").writeText(
                buildString {
                    appendLine("package com.infinitezerone.minibgm.core.data.download")
                    appendLine()
                    appendLine("/**")
                    appendLine(" * 构建期从预构建 `.so` 生成的完整性清单，随签名 APK 一起分发。")
                    appendLine(" *")
                    appendLine(" * 这是动态原生扩展的唯一信任锚：客户端只接受与当前 APK 同源、")
                    appendLine(" * 且哈希完全一致的二进制；禁止以 `latest` 等可漂移地址回退。")
                    appendLine(" */")
                    appendLine("internal object NativePluginHashes {")
                    appendLine("    val BY_ABI: Map<String, String> =")
                    appendLine("        mapOf(")
                    manifestLines.forEach { line ->
                        val (abi, hash) = line.split(' ')
                        appendLine("            \"$abi\" to \"$hash\",")
                    }
                    appendLine("        )")
                    appendLine("}")
                },
            )
        }
    }

kotlin {
    sourceSets {
        named("androidMain") {
            kotlin.srcDir(generateNativePluginHashes)
        }
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
            implementation(libs.androidx.core.ktx)
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
