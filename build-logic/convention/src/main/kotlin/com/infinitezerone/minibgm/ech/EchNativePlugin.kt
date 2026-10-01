package com.infinitezerone.minibgm.ech

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

private const val JNI_LIBS_PATH = "ech-native/jniLibs"

/** 迁移前的产物目录（生成物曾落在源码树里）。 */
private const val LEGACY_JNI_LIBS_PATH = "src/androidMain/jniLibs"
private const val STAMP_FILE_NAME = ".native-build.stamp"
private const val CRATE_NAME = "minibgm-ech"
private const val MIN_PLATFORM_API = 31
private const val SKIP_BUILD_PROPERTY = "minibgm.ech.skipBuild"
private const val NDK_DIR_PROPERTY = "minibgm.ech.ndkDir"

/** 与 tools/build-ech.*、APK 内容三者一致的 ABI 集合（32 位设备不提供 ECH，会回退 CIO）。 */
private val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")

/**
 * 把 Rust ECH 协议栈（crates/minibgm-ech）编译成 libminibgm_ech.so，并接入 Android 打包链路。
 *
 * 为什么需要这个插件：`.so` 是构建产物且 jniLibs 目录不入库（见 .gitignore），此前只有手工跑
 * tools/build-ech.sh 才会存在它 —— 全新 clone 与 CI 打出的 APK 里没有这个库，
 * `EchNativeClient.isAvailable()` 恒为 false，网络层静默回退 CIO，ECH 在发布产物里
 * 等于不存在（debug APK 因为本机跑过脚本而"看起来正常"，所以这个缺陷长期不可见）。
 *
 * 本插件把"编译 + 校验"接进构建图（jniLibs 合并任务的前置）：
 * - 工具链可用 → `cargo ndk` 重新编译；输入（Rust 源码 / Cargo.lock / ABI / API）与输出
 *   （jniLibs 目录）都声名在案，增量与最新检查交给 Gradle；
 * - `-Pminibgm.ech.skipBuild=true` → 不编译，但已有产物必须与当前 Rust 源码指纹一致；
 * - 两种情况都会校验每个 ABI 的 .so 存在且非空。
 *
 * 因此"缺 .so 却静默降级"这条假绿通道被关闭：任何打包都会失败得很难看且说明原因。
 */
class EchNativePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // 产物必须落在 build/ 而不是源码树：core/testing 的架构红线测试把 `core/*/src/**`
        // 整个声明为任务输入，生成物写进 src/ 会触发 Gradle 的隐式依赖校验失败（实测：
        // :core:testing:testAndroidHostTest 报 "Property has implicit dependency"）。
        val jniLibsDirectory = target.layout.buildDirectory.dir(JNI_LIBS_PATH)
        val projectDirectory = target.layout.projectDirectory
        val buildEchNative =
            target.tasks.register<BuildEchNativeTask>("buildEchNative") {
                group = "build"
                description = "编译 Rust ECH 原生库并校验 ABI 产物完整、与 Rust 源码同步"
                workspaceDir.set(target.rootProject.layout.projectDirectory)
                localPropertiesFile.set(target.rootProject.layout.projectDirectory.file("local.properties"))
                rustSources.from(rustSourceFiles(target))
                jniLibsDir.set(jniLibsDirectory)
                this.projectDir.set(projectDirectory)
                abis.convention(SUPPORTED_ABIS)
                platformApi.convention(MIN_PLATFORM_API)
                skipBuild.convention(
                    target.providers.gradleProperty(SKIP_BUILD_PROPERTY).map(String::toBoolean).orElse(false),
                )
                ndkDirFromProperty.convention(target.providers.gradleProperty(NDK_DIR_PROPERTY))
                ndkDirFromEnv.convention(ndkDirFromEnvironment(target))
                sdkDirFromEnv.convention(
                    target.providers
                        .environmentVariable("ANDROID_HOME")
                        .orElse(target.providers.environmentVariable("ANDROID_SDK_ROOT")),
                )
            }

        // AGP 默认只认 src/androidMain/jniLibs，这里把 build/ 下的产物目录注册成 variant 的 jniLibs 源
        target.extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
            onVariants { variant ->
                // 拿不到 jniLibs 源目录就宁可配置期失败：否则产物不进包，又是"静默没有 ECH"
                val jniLibs =
                    variant.sources.jniLibs
                        ?: throw GradleException(
                            "❌ [ech-native] AGP 未给 variant ${variant.name} 提供 jniLibs 源目录，" +
                                "无法把 libminibgm_ech.so 接进打包链路。请检查 AGP 版本兼容性。",
                        )
                jniLibs.addStaticSourceDirectory(jniLibsDirectory.get().asFile.absolutePath)
            }
        }

        // 只挂到真正消费 jniLibs 的打包任务上：preBuild 是 AGP 打包链的共同祖先，
        // 但 lint / 单元测试不应被拖进 Rust 工具链依赖，故用后缀匹配而不是 preBuild 兜底。
        target.tasks
            .matching { it.name.endsWith("JniLibFolders") || it.name.endsWith("NativeLibs") }
            .configureEach { dependsOn(buildEchNative) }
    }
}

private fun rustSourceFiles(target: Project): ConfigurableFileCollection =
    target.files(
        target.rootProject.fileTree("crates") {
            include("**/*.rs", "**/Cargo.toml", "**/Cargo.lock")
            exclude("**/target/**")
        },
        target.rootProject.file("Cargo.toml"),
        target.rootProject.file("Cargo.lock"),
    )

private fun ndkDirFromEnvironment(target: Project): Provider<String> =
    target.providers
        .environmentVariable("ANDROID_NDK_HOME")
        .orElse(target.providers.environmentVariable("ANDROID_NDK_ROOT"))
        .map { it.trim() }

abstract class BuildEchNativeTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSources: ConfigurableFileCollection

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val platformApi: Property<Int>

    @get:Input
    abstract val skipBuild: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val ndkDirFromProperty: Property<String>

    @get:Input
    @get:Optional
    abstract val ndkDirFromEnv: Property<String>

    @get:Input
    @get:Optional
    abstract val sdkDirFromEnv: Property<String>

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:Internal
    abstract val workspaceDir: DirectoryProperty

    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildNative() {
        val outputDir = jniLibsDir.get().asFile
        val expectedAbis = abis.get()
        val fingerprint = rustFingerprint()
        cleanLegacyJniLibsDir()

        if (skipBuild.get()) {
            verifyAbiArtifacts(outputDir, expectedAbis)
            verifyFingerprint(outputDir, fingerprint)
            logger.lifecycle("[ech-native] 跳过编译（-$SKIP_BUILD_PROPERTY=true），已有产物与 Rust 源码指纹一致")
            return
        }

        val ndkDir =
            resolveNdkDir()
                ?: throw GradleException(ndkMissingMessage())
        val cargo =
            findExecutable("cargo")
                ?: throw GradleException(cargoMissingMessage())
        findExecutable("cargo-ndk")
            ?: throw GradleException(
                "❌ [ech-native] 未找到 cargo-ndk，无法交叉编译 Android 产物。\n" +
                    "   安装：cargo install cargo-ndk --locked\n" +
                    "   （或先用 tools/build-ech.sh 生成 .so 后加 -P$SKIP_BUILD_PROPERTY=true 复用）",
            )

        outputDir.mkdirs()
        logger.lifecycle(
            "[ech-native] cargo-ndk 编译 ${expectedAbis.joinToString()}（NDK: ${ndkDir.absolutePath}）...",
        )
        execOperations
            .exec {
                workingDir(workspaceDir.get().asFile)
                val command =
                    buildList {
                        add(cargo.absolutePath)
                        add("ndk")
                        expectedAbis.forEach { abi ->
                            add("-t")
                            add(abi)
                        }
                        add("--platform")
                        add(platformApi.get().toString())
                        add("-o")
                        add(outputDir.absolutePath)
                        add("build")
                        add("--release")
                        // Cargo.lock 入库即视为可复现构建契约：依赖漂移必须显式改锁文件
                        add("--locked")
                        add("-p")
                        add(CRATE_NAME)
                        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                            val tempTargetDir = File(System.getProperty("java.io.tmpdir"), "minibgm-cargo-target")
                            tempTargetDir.mkdirs()
                            add("--target-dir")
                            add(tempTargetDir.absolutePath)
                        }
                    }
                commandLine(command)
                environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
                environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
                val cmakeDir = resolveCmakeDir()
                val extraPath = listOfNotNull(cargo.parentFile.absolutePath, cmakeDir?.absolutePath).joinToString(File.pathSeparator)
                environment("PATH", extraPath + File.pathSeparator + currentPath())
            }.assertNormalExitValue()

        verifyAbiArtifacts(outputDir, expectedAbis)
        writeFingerprint(outputDir, fingerprint)
    }

    /** Rust 源码 + 依赖锁 + ABI/API 的内容指纹；用于在跳过编译时识别过期产物。 */
    private fun rustFingerprint(): String {
        val root = workspaceDir.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        rustSources.files
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                digest.update(file.relativeToOrSelf(root).invariantSeparatorsPath.toByteArray())
                digest.update(0)
                digest.update(file.readBytes())
                digest.update(0)
            }
        digest.update(abis.get().joinToString(",").toByteArray())
        digest.update(0)
        digest.update(platformApi.get().toString().toByteArray())
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * 清理迁移前遗留在源码树里的产物目录。
     *
     * 生成物曾经写在 src/androidMain/jniLibs，而 AGP 默认仍会扫描该目录：残留的旧 .so
     * 会被一起合入 APK（重复或静默用旧库），正好破坏"打包必含新鲜产物"这条保证。
     * 只在目录内容确属本任务产物时才递归删除，避免误删他人放置的文件。
     */
    private fun cleanLegacyJniLibsDir() {
        val legacy = File(projectDir.get().asFile, LEGACY_JNI_LIBS_PATH)
        if (!legacy.isDirectory) return
        val leftovers = legacy.walkTopDown().filter { it.isFile }.toList()
        if (leftovers.isEmpty()) {
            legacy.deleteRecursively()
            return
        }
        val unexpected =
            leftovers.filterNot { file ->
                file.name == "libminibgm_ech.so" || file.name == STAMP_FILE_NAME
            }
        if (unexpected.isNotEmpty()) {
            logger.warn(
                "[ech-native] ${legacy.path} 存在非本任务产物，未自动清理：" +
                    unexpected.joinToString { it.name },
            )
            return
        }
        if (legacy.deleteRecursively()) {
            logger.lifecycle("[ech-native] 已清理迁移前的遗留产物目录 ${legacy.path}")
        }
    }

    private fun verifyAbiArtifacts(
        outputDir: File,
        expectedAbis: List<String>,
    ) {
        val missing =
            expectedAbis.filter { abi ->
                val library = File(outputDir, "$abi/libminibgm_ech.so")
                !library.isFile || library.length() == 0L
            }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "❌ [ech-native] 缺少原生库产物：${missing.joinToString()}（目录 ${outputDir.absolutePath}）。\n" +
                    "   这会直接导致 ECH 引擎在运行时不可用（静默回退 CIO），因此构建失败而非放行。\n" +
                    "   修复：确认 cargo-ndk 成功产出，或运行 tools/build-ech.sh。",
            )
        }
    }

    private fun verifyFingerprint(
        outputDir: File,
        fingerprint: String,
    ) {
        val stamp = File(outputDir, STAMP_FILE_NAME)
        val recorded = runCatching { stamp.readText().trim() }.getOrNull()
        if (recorded != fingerprint) {
            throw GradleException(
                "❌ [ech-native] 已有 .so 与当前 Rust 源码不一致（产物过期），但已要求跳过编译。\n" +
                    "   产物指纹：${recorded ?: "<缺失>"}\n" +
                    "   源码指纹：$fingerprint\n" +
                    "   修复：去掉 -P$SKIP_BUILD_PROPERTY=true 让本任务重新编译，或手工运行 tools/build-ech.sh。",
            )
        }
    }

    private fun writeFingerprint(
        outputDir: File,
        fingerprint: String,
    ) {
        File(outputDir, STAMP_FILE_NAME).writeText("$fingerprint\n")
    }

    private fun resolveNdkDir(): File? {
        ndkDirFromProperty
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        ndkDirFromEnv
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        return candidateNdkDirs()
            ?.filter(::isUsableNdk)
            ?.maxByOrNull { ndkSortKey(it.name) }
    }

    /**
     * SDK 的 ndk 目录下可能残留"只有 .installer 的空壳"（下载中断、或只登记未下载），
     * 它们的名字往往还是最大的版本号。若不校验可用性就按名字取最高版本，cargo-ndk 会以
     * "Error detecting NDK version" 失败，且错误信息完全指不到真正原因。
     */
    private fun isUsableNdk(directory: File): Boolean =
        File(directory, "source.properties").isFile && File(directory, "toolchains/llvm/prebuilt").isDirectory

    private fun candidateNdkDirs(): List<File>? {
        val sdkDir =
            sdkDirFromEnv.orNull?.takeIf { it.isNotBlank() }?.let(::File)
                ?: readSdkDirFromLocalProperties()
                ?: return null
        val ndkRoot = File(sdkDir, "ndk")
        if (!ndkRoot.isDirectory) return null
        return ndkRoot.listFiles { file -> file.isDirectory }?.toList().orEmpty()
    }

    private fun readSdkDirFromLocalProperties(): File? {
        val file = localPropertiesFile.orNull?.asFile?.takeIf { it.isFile } ?: return null
        val line = file.readLines().firstOrNull { it.trimStart().startsWith("sdk.dir") } ?: return null
        val value =
            line
                .substringAfter('=', "")
                .trim()
                // local.properties 是 Java Properties 方言：Windows 路径里 : 与 \ 都被转义
                .replace("\\\\", "\\")
                .replace("\\:", ":")
                .replace("\\=", "=")
        return value.takeIf { it.isNotBlank() }?.let(::File)
    }

    private fun ndkMissingMessage(): String {
        val candidates =
            candidateNdkDirs()
                ?.joinToString(separator = "\n") { dir ->
                    val state = if (isUsableNdk(dir)) "可用" else "不可用（缺少 source.properties 或 toolchains）"
                    "     - ${dir.name}：$state"
                }
                .orEmpty()
        val listed = if (candidates.isBlank()) "     （SDK 下没有 ndk/ 目录或无法定位 SDK）" else candidates
        return "❌ [ech-native] 未检测到可用的 Android NDK，无法编译 libminibgm_ech.so。\n" +
            "   解析顺序：-P$NDK_DIR_PROPERTY → ANDROID_NDK_HOME / ANDROID_NDK_ROOT → " +
            "ANDROID_HOME|ANDROID_SDK_ROOT/local.properties(sdk.dir) 下的 ndk/<最高可用版本>。\n" +
            "   当前 SDK 中的候选：\n" +
            "$listed\n" +
            "   修复：sdkmanager --install \"ndk;27.2.12479018\"，或显式传 -P$NDK_DIR_PROPERTY=<ndk 路径>。"
    }

    private fun resolveCmakeDir(): File? {
        findExecutable("cmake")?.parentFile?.let { return it }
        val sdkDir =
            sdkDirFromEnv.orNull?.takeIf { it.isNotBlank() }?.let(::File)
                ?: readSdkDirFromLocalProperties()
                ?: return null
        val cmakeRoot = File(sdkDir, "cmake")
        if (!cmakeRoot.isDirectory) return null
        return cmakeRoot.listFiles { file -> file.isDirectory }
            ?.map { File(it, "bin") }
            ?.firstOrNull { File(it, "cmake.exe").isFile || File(it, "cmake").isFile }
    }

    private fun cargoMissingMessage(): String =
        "❌ [ech-native] 未找到 Rust 工具链（cargo），无法编译 libminibgm_ech.so。\n" +
            "   安装：https://rustup.rs（并 rustup target add aarch64-linux-android x86_64-linux-android）。\n" +
            "   若本机已有该库产物，可加 -P$SKIP_BUILD_PROPERTY=true 跳过编译（仍会校验产物与源码一致）。"
}

private fun currentPath(): String {
    val raw = System.getenv("PATH").orEmpty()
    return raw.split(File.pathSeparator)
        .filter { path ->
            !path.contains("Hostx64\\x86", ignoreCase = true) &&
                !path.contains("Hostx86", ignoreCase = true) &&
                !path.contains("pcsuite", ignoreCase = true)
        }
        .joinToString(File.pathSeparator)
}

/** 在 PATH 与 ~/.cargo/bin 里定位可执行文件（Windows 需要补 .exe）。 */
private fun findExecutable(name: String): File? {
    val directories =
        buildList {
            addAll(currentPath().split(File.pathSeparator).filter { it.isNotBlank() })
            System.getProperty("user.home")?.let { add("$it/.cargo/bin") }
        }
    val suffixes =
        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            listOf(".exe", ".cmd", ".bat", "")
        } else {
            listOf("")
        }
    return directories
        .asSequence()
        .flatMap { directory -> suffixes.asSequence().map { suffix -> File(directory, name + suffix) } }
        .firstOrNull { it.isFile }
}

/** 版本排序键：把 NDK 目录名各段左侧补零，"30.0.14904198" > "9.0.1" 这类字符串比较才成立。 */
private fun ndkSortKey(version: String): String = version.split('.').joinToString(".") { it.padStart(6, '0') }
