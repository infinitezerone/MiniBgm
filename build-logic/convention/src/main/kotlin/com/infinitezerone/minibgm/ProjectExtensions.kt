package com.infinitezerone.minibgm

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun Project.androidLibrary(configure: LibraryExtension.() -> Unit) {
    extensions.configure<LibraryExtension>(configure)
}

/** AGP KMP 库模块的 Android 配置入口（namespace 等），经 finalizeDsl 延迟到 variant 创建前执行 */
fun Project.kmpAndroidLibrary(configure: KotlinMultiplatformAndroidLibraryExtension.() -> Unit) {
    extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
        finalizeDsl(configure)
    }
}

fun Project.androidApplication(configure: ApplicationExtension.() -> Unit) {
    extensions.configure<ApplicationExtension>(configure)
}

/**
 * 原生库 ABI 唯一真源：仓库根 `native/abis.txt`。
 *
 * `tools/build-native` 与 `.github/workflows/release.yml` 读的是同一份文件；此处经
 * `providers.fileContents` 读取，内容变化会正确失效 configuration cache，不做裸文件访问。
 * 行尾 `#` 注释与空行忽略。
 */
fun Project.nativeAbis(): List<String> {
    val manifest = layout.settingsDirectory.file("native/abis.txt")
    val abis =
        providers
            .fileContents(manifest)
            .asText
            .orNull
            ?.lineSequence()
            ?.map { it.substringBefore('#').trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()
    check(abis.isNotEmpty()) {
        "❌ 未能从原生库 ABI 真源文件读取到任何 ABI（缺失或为空）：${manifest.asFile.absolutePath}"
    }
    return abis
}

/**
 * 挂载由 `tools/build-native` 预构建的原生库目录，并在打包合并 jniLibs 前校验 `.so` 存在且非空。
 *
 * 构建解耦原则：Gradle 仅作为二进制产物消费者，绝不跨界调度 Cargo / Android NDK。
 *
 * @param requiredAbis 需要校验的架构集合；默认 `null` 表示读取仓库根 `native/abis.txt`（唯一真源）
 */
fun Project.registerPrebuiltJniLibs(
    buildSubPath: String,
    libraryFileName: String,
    requiredAbis: List<String>? = null,
) {
    val jniLibsDirectory = layout.buildDirectory.dir(buildSubPath)
    val abis = requiredAbis ?: nativeAbis()
    val verifyTask =
        tasks.register("verifyPrebuiltJniLibs") {
            group = "verification"
            description = "校验预构建原生库 $libraryFileName 已就绪（架构集见 native/abis.txt）"
            inputs.property("nativeAbis", abis)
            doLast {
                val baseDir = jniLibsDirectory.get().asFile
                val missing =
                    abis.filter { abi ->
                        val so = baseDir.resolve("$abi/$libraryFileName")
                        !so.isFile || so.length() == 0L
                    }
                if (missing.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "❌ [$path] 缺少预构建原生库 $libraryFileName（缺失架构：${missing.joinToString()}，目录：${baseDir.absolutePath}）。\n" +
                            "   请先运行独立构建脚本：bash tools/build-native（Windows：.\\tools\\build-native.ps1）",
                    )
                }
            }
        }

    extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
        onVariants { variant ->
            val jniLibs =
                variant.sources.jniLibs
                    ?: throw org.gradle.api.GradleException(
                        "❌ [$path] AGP 未给 variant ${variant.name} 提供 jniLibs 源目录，无法接入 $libraryFileName。",
                    )
            jniLibs.addStaticSourceDirectory(jniLibsDirectory.get().asFile.absolutePath)
        }
    }

    tasks.configureEach {
        if (name.endsWith("JniLibFolders") || name.endsWith("NativeLibs")) {
            dependsOn(verifyTask)
        }
    }
}

/**
 * [registerPrebuiltJniLibs] 的纯 Android Library 模块变体（非 KMP 模块走
 * `LibraryAndroidComponentsExtension`）。
 */
fun Project.registerPrebuiltJniLibsForLibrary(
    buildSubPath: String,
    libraryFileName: String,
) {
    val jniLibsDirectory = layout.buildDirectory.dir(buildSubPath)
    val abis = nativeAbis()
    val verifyTask =
        tasks.register("verifyPrebuiltLibraryJniLibs") {
            group = "verification"
            description = "校验预构建原生库 $libraryFileName 已就绪（架构集见 native/abis.txt）"
            inputs.property("nativeAbis", abis)
            doLast {
                val baseDir = jniLibsDirectory.get().asFile
                val missing =
                    abis.filter { abi ->
                        val so = baseDir.resolve("$abi/$libraryFileName")
                        !so.isFile || so.length() == 0L
                    }
                if (missing.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "❌ [$path] 缺少预构建原生库 $libraryFileName（缺失架构：${missing.joinToString()}，目录：${baseDir.absolutePath}）。\n" +
                            "   请先运行独立构建脚本：bash tools/build-native（Windows：.\\tools\\build-native.ps1）",
                    )
                }
            }
        }

    extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
        onVariants { variant ->
            variant.sources.jniLibs
                ?: throw org.gradle.api.GradleException(
                    "❌ [$path] AGP 未给 variant ${variant.name} 提供 jniLibs 源目录，无法接入 $libraryFileName。",
                )
            variant.sources.jniLibs?.addStaticSourceDirectory(jniLibsDirectory.get().asFile.absolutePath)
        }
    }

    tasks.configureEach {
        if (name.endsWith("JniLibFolders") || name.endsWith("NativeLibs")) {
            dependsOn(verifyTask)
        }
    }
}

/**
 * 按构建类型控制按需扩展原生库（`libminibgm_torrent.so`）的打包策略：
 * - `debug` 默认保留内置，使本地修改 Rust 源码并运行 `tools/build-native` 后可直接安装联调（如需模拟未安装态走云端下载，直接在应用内「设置 → 开发者调试面板」一键切换）；
 * - `release` 始终排除该库（瘦身 ~20MB），由 GitHub Release 独立分发 `.so` 资产并经 `System.load` 动态装载。
 */
fun Project.configureOnDemandNativePackaging() {
    extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            if (variant.buildType == "release") {
                variant.packaging.jniLibs.excludes.add("**/libminibgm_torrent.so")
            }
        }
    }
}

